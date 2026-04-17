import React from 'react';

export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-[50vh] flex-col items-center justify-center p-6 text-center">
          <div className="mb-4 text-5xl">⚠️</div>
          <h2 className="mb-2 font-display text-2xl font-bold text-white">Something went wrong</h2>
          <p className="mb-6 text-white/60">An unexpected error occurred while rendering this page.</p>
          <button
            onClick={() => window.location.reload()}
            className="rounded-lg bg-accent px-6 py-2.5 text-sm font-semibold text-surface transition-colors hover:bg-accent-muted"
          >
            Reload Page
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
