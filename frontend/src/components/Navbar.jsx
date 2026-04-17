import { Link, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useApp } from '../context/AppContext';
import { useState, useEffect } from 'react';

export default function Navbar() {
  const { auth, logout } = useApp();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();

  // Close mobile menu when route changes
  useEffect(() => {
    setMobileMenuOpen(false);
  }, [location.pathname]);

  const navLinks = [
    { name: 'Platforms', path: '/platforms' },
    { name: 'Alerts', path: '/alerts' },
    { name: 'Watchlist', path: '/watchlist' }
  ];

  // Show Dashboard link only when logged in
  const isLoggedIn = !!auth?.token;

  return (
    <motion.header
      initial={{ y: -20, opacity: 0 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.4 }}
      className="sticky top-0 z-50 border-b border-white/10 bg-surface/95 backdrop-blur-card"
    >
      <nav className="mx-auto flex max-w-7xl items-center justify-between px-4 py-4 sm:px-6 lg:px-8" aria-label="Main navigation">
        <Link to="/" className="font-display text-xl tracking-tight text-accent sm:text-2xl z-10">
          PRICE COMP
        </Link>

        {/* ── Desktop Navigation ── */}
        <div className="hidden md:flex items-center gap-6">
          {navLinks.map((link) => (
            <Link
              key={link.name}
              to={link.path}
              className="text-sm font-medium text-white/80 transition hover:text-accent focus:text-accent"
            >
              {link.name}
            </Link>
          ))}

          {/* Dashboard — logged-in users only */}
          {isLoggedIn && (
            <Link
              to="/dashboard"
              className="text-sm font-medium text-white/80 transition hover:text-accent focus:text-accent"
            >
              Dashboard
            </Link>
          )}
          
          {auth ? (
            <button
              type="button"
              onClick={logout}
              className="text-sm font-medium text-white/70 transition hover:text-white focus:text-white"
              title={auth.email || 'Signed in'}
            >
              Logout
            </button>
          ) : (
            <Link
              to="/auth"
              className="rounded-lg bg-white/5 px-3 py-2 text-sm font-medium text-white/80 transition hover:bg-white/10 hover:text-white"
            >
              Sign in
            </Link>
          )}

          <Link
            to="/analytics"
            className="group relative flex h-8 w-8 items-center justify-center rounded-lg bg-white/5 transition hover:bg-white/10"
            title="SaaS Analytics"
          >
            <svg className="h-4 w-4 text-white/60 group-hover:text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
            </svg>
          </Link>
        </div>

        {/* ── Mobile Hamburger Button ── */}
        <button
          className="md:hidden z-10 flex h-10 w-10 items-center justify-center rounded-lg bg-white/5 text-white/80 hover:bg-white/10 hover:text-white"
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
        >
          {mobileMenuOpen ? (
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          ) : (
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          )}
        </button>
      </nav>

      {/* ── Mobile Dropdown Menu ── */}
      <AnimatePresence>
        {mobileMenuOpen && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="md:hidden border-t border-white/10 bg-surface-elevated/95 backdrop-blur-3xl"
          >
            <div className="flex flex-col gap-2 px-4 py-6">
              {navLinks.map((link) => (
                <Link
                  key={link.name}
                  to={link.path}
                  className="rounded-lg px-4 py-3 text-base font-medium text-white/80 hover:bg-white/5 hover:text-accent"
                >
                  {link.name}
                </Link>
              ))}
              
              <Link
                to="/analytics"
                className="rounded-lg px-4 py-3 text-base font-medium text-white/80 hover:bg-white/5 hover:text-accent"
              >
                Analytics
              </Link>

              {/* Dashboard — logged-in users only */}
              {isLoggedIn && (
                <Link
                  to="/dashboard"
                  className="rounded-lg px-4 py-3 text-base font-medium text-white/80 hover:bg-white/5 hover:text-accent"
                >
                  Dashboard
                </Link>
              )}
              
              <div className="my-2 h-px w-full bg-white/10" />

              {auth ? (
                <>
                  <div className="px-4 py-2 text-xs font-mono text-white/40">{auth.email}</div>
                  <button
                    onClick={() => {
                      logout();
                      setMobileMenuOpen(false);
                    }}
                    className="rounded-lg px-4 py-3 text-left text-base font-medium text-danger hover:bg-danger/10"
                  >
                    Logout
                  </button>
                </>
              ) : (
                <Link
                  to="/auth"
                  className="rounded-lg bg-accent/10 px-4 py-3 text-base font-medium text-accent hover:bg-accent/20"
                >
                  Sign in / Create Account
                </Link>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.header>
  );
}
