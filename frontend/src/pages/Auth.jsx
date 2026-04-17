import { useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import { useApp } from '../context/AppContext';

export default function Auth() {
  const navigate = useNavigate();
  const { login, register, showToast } = useApp();
  const [mode, setMode] = useState('login'); // login | register
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const title = useMemo(() => (mode === 'login' ? 'Sign in' : 'Create account'), [mode]);

  const onSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      if (mode === 'login') {
        await login(email, password);
        showToast('Signed in', 'success');
      } else {
        await register(email, password);
        showToast('Account created', 'success');
      }
      navigate('/');
    } catch (err) {
      const message = err.response?.data?.error || err.response?.data?.detail || err.message || 'Auth failed';
      showToast(message, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main className="mx-auto max-w-lg px-4 py-10 sm:px-6 lg:px-8">
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
        <h1 className="font-display text-2xl uppercase tracking-tight text-white">{title}</h1>
        <p className="mt-2 text-white/60">
          Sign in to sync watchlist/alerts and unlock higher daily scraping limits.
        </p>

        <div className="mt-6 inline-flex rounded-lg bg-white/5 p-1">
          <button
            type="button"
            onClick={() => setMode('login')}
            className={`rounded-md px-4 py-2 text-sm ${mode === 'login' ? 'bg-white/10 text-white' : 'text-white/60 hover:text-white'}`}
          >
            Login
          </button>
          <button
            type="button"
            onClick={() => setMode('register')}
            className={`rounded-md px-4 py-2 text-sm ${mode === 'register' ? 'bg-white/10 text-white' : 'text-white/60 hover:text-white'}`}
          >
            Register
          </button>
        </div>

        <form onSubmit={onSubmit} className="mt-6 space-y-4 rounded-xl border border-white/10 bg-surface/60 p-6">
          <label className="block">
            <span className="text-xs text-white/60">Email</span>
            <input
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              type="email"
              required
              className="mt-1 w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-white outline-none focus:border-accent"
            />
          </label>
          <label className="block">
            <span className="text-xs text-white/60">Password</span>
            <input
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type="password"
              required
              minLength={8}
              className="mt-1 w-full rounded-lg border border-white/10 bg-white/5 px-3 py-2 text-white outline-none focus:border-accent"
            />
          </label>
          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-accent px-4 py-2 font-display text-sm uppercase text-surface hover:bg-accent-muted disabled:opacity-60"
          >
            {loading ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>
      </motion.div>
    </main>
  );
}

