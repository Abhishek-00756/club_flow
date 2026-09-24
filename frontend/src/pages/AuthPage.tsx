import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/lib/auth';
import { ApiError, errorMessage } from '@/lib/api';
import { Button, Field, Input, Notice } from '@/components/ui';

export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [clubCode, setClubCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const isLogin = mode === 'login';

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      if (isLogin) await login(email.trim(), password);
      else await register({ name: name.trim(), email: email.trim(), password, clubCode: clubCode.trim() });
      navigate(from, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      if (err instanceof ApiError) setFieldErrors(err.fieldErrors);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <div className="hidden flex-col justify-between bg-ink p-12 text-white lg:flex">
        <div className="flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-marker-600">
            <svg width="22" height="22" viewBox="0 0 32 32" aria-hidden="true">
              <path d="M6 22c4 0 4.5-12 8.5-12S18 22 22 22s3-6 4-6" fill="none" stroke="#F4D954" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </span>
          <span className="font-display text-xl font-bold">ClubFlow</span>
        </div>

        <div className="max-w-md">
          <h1 className="font-display text-4xl font-bold leading-tight">Everyone knows what they own and when it is due.</h1>
          <p className="mt-4 text-lg text-white/70">
            Assign tasks, review submitted work, and let reminders do the chasing so the secretary does not have to.
          </p>
          <ul className="mt-8 space-y-3 text-white/80">
            {['Tasks move from to do to approved, with a reason whenever work is sent back', 'Reminders go out a day before, three hours before, and when a deadline is missed', 'Event checklists turn into assigned tasks in one click'].map((t) => (
              <li key={t} className="flex gap-3">
                <span className="mt-2 h-1.5 w-1.5 flex-none rounded-full bg-hl-300" />
                {t}
              </li>
            ))}
          </ul>
        </div>

        <p className="text-sm text-white/50">Built for college clubs.</p>
      </div>

      <div className="flex items-center justify-center bg-chalk-100 px-5 py-10">
        <div className="w-full max-w-sm">
          <h2 className="font-display text-2xl font-bold text-ink">{isLogin ? 'Sign in to ClubFlow' : 'Join your club'}</h2>
          <p className="mt-1.5 text-sm text-ink-mute">
            {isLogin ? 'Use the email your club admin registered.' : 'Ask your secretary for the club join code.'}
          </p>

          <form onSubmit={submit} className="mt-7 space-y-4">
            {error && <Notice tone="rose">{error}</Notice>}
            {!isLogin && (
              <Field label="Full name" error={fieldErrors.name}>
                <Input value={name} onChange={(e) => setName(e.target.value)} required autoComplete="name" />
              </Field>
            )}
            <Field label="Email" error={fieldErrors.email}>
              <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
            </Field>
            <Field label="Password" hint={isLogin ? undefined : 'At least 8 characters.'} error={fieldErrors.password}>
              <Input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={isLogin ? undefined : 8}
                autoComplete={isLogin ? 'current-password' : 'new-password'}
              />
            </Field>
            {!isLogin && (
              <Field label="Club join code" error={fieldErrors.clubCode}>
                <Input value={clubCode} onChange={(e) => setClubCode(e.target.value)} required className="uppercase" autoCapitalize="characters" />
              </Field>
            )}
            <Button type="submit" variant="primary" className="w-full" loading={busy}>
              {isLogin ? 'Sign in' : 'Create account'}
            </Button>
          </form>

          <p className="mt-6 text-sm text-ink-mute">
            {isLogin ? (
              <>
                New to the club?{' '}
                <Link to="/register" className="font-semibold text-marker-700 hover:underline">
                  Join with a code
                </Link>
              </>
            ) : (
              <>
                Already have an account?{' '}
                <Link to="/login" className="font-semibold text-marker-700 hover:underline">
                  Sign in
                </Link>
              </>
            )}
          </p>
        </div>
      </div>
    </div>
  );
}
