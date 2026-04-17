package com.pricecomparison.service;

import com.pricecomparison.model.Price;
import com.pricecomparison.model.PriceHistory;
import com.pricecomparison.model.Product;
import com.pricecomparison.model.TrackedProduct;
import com.pricecomparison.model.PriceAlert;
import com.pricecomparison.repository.PriceHistoryRepository;
import com.pricecomparison.repository.PriceRepository;
import com.pricecomparison.repository.ProductRepository;
import com.pricecomparison.repository.TrackedProductRepository;
import com.pricecomparison.repository.PriceAlertRepository;
import com.pricecomparison.dto.PriceResponse;
import com.pricecomparison.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceTrackingService {

    private static final BigDecimal PRICE_CHANGE_THRESHOLD = new BigDecimal("0.05"); // 5%
    private static final int HISTORY_RETENTION_DAYS = 90;
    private static final long REFRESH_COOLDOWN_MINUTES = 60; // Don't re-fetch within 1 hour

    private final TrackedProductRepository trackedProductRepository;
    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceAlertRepository priceAlertRepository;
    private final ProductSearchService productSearchService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@pricecompare.com}")
    private String fromEmail;

    /* ════════════════════════════════════════════════════════════════════
     *  SCHEDULED: Refresh all tracked products (every 6 hours)
     * ════════════════════════════════════════════════════════════════════ */

    @Scheduled(cron = "${scheduler.price-tracking-cron:0 0 */6 * * *}")
    @Transactional
    public void refreshPricesForTrackedProducts() {
        List<TrackedProduct> tracked = trackedProductRepository.findAll();
        log.info("═══ Scheduled price refresh: {} tracked products ═══", tracked.size());

        int successCount = 0;
        int failCount = 0;

        for (TrackedProduct tp : tracked) {
            try {
                updatePricesForProduct(tp.getProduct());
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("Failed to update prices for product {} ('{}'): {}",
                        tp.getProduct().getId(), tp.getProduct().getName(), e.getMessage());
            }
        }

        log.info("═══ Price refresh complete: {} succeeded, {} failed ═══", successCount, failCount);

        // Check active price alerts after updating all prices
        checkPriceAlerts();

        // Cleanup old history entries beyond retention period
        cleanupOldHistory();
    }

    /* ════════════════════════════════════════════════════════════════════
     *  PUBLIC: Refresh a single product (on-demand, from controllers)
     * ════════════════════════════════════════════════════════════════════ */

    @Transactional
    public void refreshSingleProduct(Long productId) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isEmpty()) {
            log.warn("Cannot refresh: product {} not found", productId);
            return;
        }

        Product product = productOpt.get();

        // Check cooldown — don't re-fetch if we updated within the last hour
        List<Price> existingPrices = priceRepository.findByProductIdOrderByPriceAsc(productId);
        if (!existingPrices.isEmpty()) {
            Instant latestUpdate = existingPrices.stream()
                    .map(Price::getRecordedAt)
                    .filter(r -> r != null)
                    .max(Instant::compareTo)
                    .orElse(Instant.EPOCH);

            if (latestUpdate.plus(REFRESH_COOLDOWN_MINUTES, ChronoUnit.MINUTES).isAfter(Instant.now())) {
                log.debug("Product {} was refreshed recently ({}), skipping", productId, latestUpdate);
                return;
            }
        }

        log.info("Refreshing prices for product {} ('{}')", productId, product.getName());
        updatePricesForProduct(product);
        
        // Check alerts specifically for this product
        checkPriceAlerts();
    }

    /* ════════════════════════════════════════════════════════════════════
     *  CORE: Update prices for a product via SerpAPI search
     * ════════════════════════════════════════════════════════════════════ */

    public void updatePricesForProduct(Product product) {
        try {
            // Use ProductSearchService to get fresh prices from SerpAPI
            ProductResponse searchResult = productSearchService.searchByQuery(product.getName());

            if (searchResult.getPrices() == null || searchResult.getPrices().isEmpty()) {
                log.warn("No prices returned for product {} ('{}')", product.getId(), product.getName());
                return;
            }

            int updatedCount = 0;
            int newCount = 0;
            int historyCount = 0;

            for (PriceResponse priceResponse : searchResult.getPrices()) {
                if (priceResponse.getPrice() == null || priceResponse.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // Skip zero/null prices (fallback placeholders)
                }

                // Find existing price for this platform
                List<Price> existingPrices = priceRepository.findByProductIdAndPlatformIgnoreCase(
                        product.getId(), priceResponse.getPlatform());
                Price existingPrice = existingPrices.isEmpty() ? null : existingPrices.get(0);

                if (existingPrice != null) {
                    // Always record to price history (even if change is small)
                    recordPriceHistory(product, priceResponse.getPlatform(), priceResponse.getPrice());
                    historyCount++;

                    // Update current price record
                    existingPrice.setPrice(priceResponse.getPrice());
                    existingPrice.setAvailability(priceResponse.getAvailability());
                    existingPrice.setProductUrl(priceResponse.getProductUrl());
                    existingPrice.setSource(priceResponse.getSource());
                    priceRepository.save(existingPrice);
                    updatedCount++;
                } else {
                    // Create new price entry
                    Price newPrice = Price.builder()
                            .product(product)
                            .platform(priceResponse.getPlatform())
                            .price(priceResponse.getPrice())
                            .currency(priceResponse.getCurrency())
                            .productUrl(priceResponse.getProductUrl())
                            .availability(priceResponse.getAvailability() != null ? priceResponse.getAvailability() : true)
                            .source(priceResponse.getSource())
                            .build();
                    priceRepository.save(newPrice);
                    newCount++;

                    // Record initial price history point
                    recordPriceHistory(product, priceResponse.getPlatform(), priceResponse.getPrice());
                    historyCount++;
                }
            }

            log.info("Product {} ('{}'): {} prices updated, {} new, {} history records saved",
                    product.getId(), product.getName(), updatedCount, newCount, historyCount);

        } catch (Exception e) {
            log.warn("Could not update prices for product {} ('{}'): {}",
                    product.getId(), product.getName(), e.getMessage());
        }
    }

    /* ════════════════════════════════════════════════════════════════════
     *  PRICE HISTORY: Record a single data point
     * ════════════════════════════════════════════════════════════════════ */

    private void recordPriceHistory(Product product, String platform, BigDecimal price) {
        PriceHistory entry = PriceHistory.builder()
                .product(product)
                .platform(platform)
                .price(price)
                .build();
        priceHistoryRepository.save(entry);
    }

    /* ════════════════════════════════════════════════════════════════════
     *  PRICE ALERTS: Scheduled check for all active alerts
     * ════════════════════════════════════════════════════════════════════ */

    @Scheduled(cron = "${scheduler.alert-check-cron:0 0 */6 * * *}")
    @Transactional
    public void checkPriceAlerts() {
        List<PriceAlert> activeAlerts = priceAlertRepository.findByActiveTrue();
        if (activeAlerts.isEmpty()) return;

        log.info("Checking {} active price alerts...", activeAlerts.size());
        int triggeredCount = 0;

        for (PriceAlert alert : activeAlerts) {
            try {
                // Determine current best price
                List<Price> currentPrices = priceRepository.findByProductIdOrderByPriceAsc(alert.getProduct().getId());
                BigDecimal currentTargetPrice = null;
                String productUrl = "";

                for (Price p : currentPrices) {
                    if (p.getPrice() != null && p.getPrice().compareTo(BigDecimal.ZERO) > 0 && !"fallback".equals(p.getSource())) {
                        currentTargetPrice = p.getPrice();
                        productUrl = p.getProductUrl();
                        break;
                    }
                }

                if (currentTargetPrice != null && currentTargetPrice.compareTo(alert.getTargetPrice()) <= 0) {
                    // Alert condition met!
                    alert.setActive(false);
                    alert.setTriggeredAt(Instant.now());
                    alert.setLastNotifiedAt(Instant.now());
                    priceAlertRepository.save(alert);

                    triggeredCount++;
                    log.info("🔔 Alert triggered for product '{}': ₹{} <= ₹{}",
                            alert.getProduct().getName(), currentTargetPrice, alert.getTargetPrice());

                    if (alert.getUserEmail() != null && !alert.getUserEmail().isBlank()) {
                        sendAlertEmail(alert, currentTargetPrice, productUrl);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to process alert {}: {}", alert.getId(), e.getMessage());
            }
        }
        
        if (triggeredCount > 0) {
            log.info("Finished alert check. {} alerts triggered.", triggeredCount);
        }
    }

    private void sendAlertEmail(PriceAlert alert, BigDecimal currentPrice, String productUrl) {
        if (mailSender == null) {
            log.debug("No JavaMailSender configured; skipping email notification for alert {}", alert.getId());
            return;
        }

        String recipient = alert.getUserEmail() != null ? alert.getUserEmail() : alert.getUserId();
        if (recipient == null || !recipient.contains("@")) {
            log.warn("Invalid email address for alert {}: {}", alert.getId(), recipient);
            return;
        }

        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setFrom(fromEmail);
            email.setTo(recipient);
            email.setSubject(String.format("Price Drop Alert: %s is now ₹%s", 
                    alert.getProduct().getName(), currentPrice));
            
            String body = String.format(
                    "The price of %s has dropped to ₹%s \n" +
                    "Target Price: ₹%s \n" +
                    "Link: %s\n\n" +
                    "Happy Shopping!\n" +
                    "Manage your alerts at: %s/alerts",
                    alert.getProduct().getName(), currentPrice, alert.getTargetPrice(), 
                    productUrl != null && !productUrl.isBlank() ? productUrl : "Visit app to view deals",
                    "http://localhost:5173"
            );
            email.setText(body);
            
            mailSender.send(email);
            log.info("Sent alert email to {} for product {}", recipient, alert.getProduct().getName());
        } catch (Exception e) {
            log.error("Failed to send alert email to {}: {}", recipient, e.getMessage());
        }
    }

    /* ════════════════════════════════════════════════════════════════════
     *  CLEANUP: Remove old history beyond retention window
     * ════════════════════════════════════════════════════════════════════ */

    @Transactional
    public void cleanupOldHistory() {
        Instant cutoff = Instant.now().minus(HISTORY_RETENTION_DAYS, ChronoUnit.DAYS);
        var productIds = priceHistoryRepository.findAll().stream()
                .map(ph -> ph.getProduct().getId())
                .distinct()
                .toList();
        for (Long productId : productIds) {
            priceHistoryRepository.deleteByProductIdAndRecordedAtBefore(productId, cutoff);
        }
        log.debug("Cleaned up price history older than {} days", HISTORY_RETENTION_DAYS);
    }
}
