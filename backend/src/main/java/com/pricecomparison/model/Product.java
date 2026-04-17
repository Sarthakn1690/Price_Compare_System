package com.pricecomparison.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import jakarta.persistence.Convert;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(length = 200)
    private String brand;

    @Column(length = 100)
    private String category;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "specifications", columnDefinition = "TEXT")
    private Map<String, String> specifications;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @CreationTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
