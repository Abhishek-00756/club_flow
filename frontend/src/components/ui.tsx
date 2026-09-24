import {
  useEffect,
  useRef,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { Icon, type IconName } from './Icon';
import { initials } from '@/lib/format';

export function cx(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ');
}

// ---------- buttons ----------

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-marker-600 text-white hover:bg-marker-700 disabled:bg-marker-600/50',
  secondary: 'bg-white text-ink border border-chalk-300 hover:bg-chalk-100 disabled:text-ink-faint',
  ghost: 'text-ink-soft hover:bg-chalk-200 disabled:text-ink-faint',
  danger: 'bg-white text-rose-700 border border-rose-200 hover:bg-rose-50 disabled:text-rose-300',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: 'sm' | 'md';
  icon?: IconName;
  loading?: boolean;
}

export function Button({ variant = 'secondary', size = 'md', icon, loading, className, children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      className={cx(
        'inline-flex items-center justify-center gap-2 rounded-lg font-semibold transition-colors',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marker-500',
        'disabled:cursor-not-allowed',
        size === 'sm' ? 'h-8 px-3 text-sm' : 'h-10 px-4 text-sm',
        VARIANTS[variant],
        className,
      )}
    >
      {loading ? <Spinner size={14} /> : icon ? <Icon name={icon} size={size === 'sm' ? 15 : 17} /> : null}
      {children}
    </button>
  );
}

export function IconButton({
  icon,
  label,
  className,
  ...rest
}: { icon: IconName; label: string } & ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      {...rest}
      className={cx(
        'inline-flex h-9 w-9 items-center justify-center rounded-lg text-ink-soft transition-colors hover:bg-chalk-200',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-marker-500 disabled:opacity-40',
        className,
      )}
    >
      <Icon name={icon} />
    </button>
  );
}

// ---------- surfaces ----------

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cx('rounded-xl border border-chalk-200 bg-white shadow-card', className)}>{children}</div>;
}

export function Section({
  title,
  action,
  children,
  className,
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <Card className={className}>
      <div className="flex items-center justify-between gap-3 border-b border-chalk-200 px-5 py-3.5">
        <h2 className="font-display text-base font-semibold text-ink">{title}</h2>
        {action}
      </div>
      {children}
    </Card>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0">
        <h1 className="font-display text-2xl font-bold tracking-tight text-ink sm:text-[1.75rem]">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-ink-mute">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export type Tone = 'neutral' | 'sky' | 'amber' | 'violet' | 'green' | 'rose' | 'yellow';

const TONES: Record<Tone, string> = {
  neutral: 'bg-chalk-200 text-ink-soft',
  sky: 'bg-sky-100 text-sky-800',
  amber: 'bg-amber-100 text-amber-900',
  violet: 'bg-violet-100 text-violet-800',
  green: 'bg-emerald-100 text-emerald-800',
  rose: 'bg-rose-100 text-rose-800',
  yellow: 'bg-hl-100 text-ink',
};

export function Badge({ tone = 'neutral', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 whitespace-nowrap rounded-md px-2 py-0.5 text-xs font-semibold',
        TONES[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

export function Avatar({ name, size = 28 }: { name: string; size?: number }) {
  return (
    <span
      title={name}
      style={{ width: size, height: size, fontSize: size * 0.4 }}
      className="inline-flex flex-none items-center justify-center rounded-full bg-marker-100 font-semibold text-marker-700"
    >
      {initials(name)}
    </span>
  );
}

export function AvatarStack({ names, max = 3 }: { names: string[]; max?: number }) {
  const shown = names.slice(0, max);
  const extra = names.length - shown.length;
  return (
    <span className="flex items-center">
      {shown.map((n, i) => (
        <span key={n + i} className={cx('rounded-full ring-2 ring-white', i > 0 && '-ml-2')}>
          <Avatar name={n} size={26} />
        </span>
      ))}
      {extra > 0 && (
        <span className="-ml-2 inline-flex h-[26px] items-center rounded-full bg-chalk-200 px-1.5 text-[11px] font-semibold text-ink-soft ring-2 ring-white">
          +{extra}
        </span>
      )}
    </span>
  );
}

// ---------- form controls ----------

const CONTROL =
  'w-full rounded-lg border border-chalk-300 bg-white px-3 text-sm text-ink placeholder:text-ink-faint ' +
  'focus:border-marker-500 focus:outline-none focus:ring-2 focus:ring-marker-500/25 disabled:bg-chalk-100 disabled:text-ink-mute';

export function Field({
  label,
  hint,
  error,
  children,
  className,
  group,
}: {
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
  className?: string;
  /** Use for a field that holds several controls (a checklist), so it is not wrapped in one <label>. */
  group?: boolean;
}) {
  const Tag: 'div' | 'label' = group ? 'div' : 'label';
  return (
    <Tag className={cx('block', className)}>
      <span className="mb-1.5 block text-sm font-semibold text-ink-soft">{label}</span>
      {children}
      {hint && !error && <span className="mt-1 block text-xs text-ink-mute">{hint}</span>}
      {error && <span className="mt-1 block text-xs font-medium text-rose-700">{error}</span>}
    </Tag>
  );
}

export function Input({ className, ...rest }: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...rest} className={cx(CONTROL, 'h-10', className)} />;
}

export function Textarea({ className, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...rest} className={cx(CONTROL, 'min-h-[88px] py-2', className)} />;
}

export function Select({ className, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select {...rest} className={cx(CONTROL, 'h-10 pr-8', className)}>
      {children}
    </select>
  );
}

export function Checkbox({
  checked,
  onChange,
  label,
  disabled,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: ReactNode;
  disabled?: boolean;
}) {
  return (
    <label className={cx('flex items-center gap-2.5 text-sm text-ink', disabled && 'opacity-60')}>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
        className="h-4 w-4 rounded border-chalk-300 accent-marker-600"
      />
      {label}
    </label>
  );
}

export function SearchInput({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div className="relative">
      <Icon name="search" size={16} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-ink-faint" />
      <Input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder ?? 'Search'}
        className="pl-9"
        aria-label={placeholder ?? 'Search'}
      />
    </div>
  );
}

// ---------- feedback ----------

export function Spinner({ size = 18 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" className="animate-spin" aria-label="Loading" role="status">
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeOpacity="0.25" strokeWidth="3" />
      <path d="M21 12a9 9 0 00-9-9" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
    </svg>
  );
}

export function Loading({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-16 text-sm text-ink-mute">
      <Spinner /> {label}
    </div>
  );
}

export function ErrorNote({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex items-center justify-between gap-3 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
      <span>{message}</span>
      {onRetry && (
        <Button size="sm" variant="secondary" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function EmptyState({ icon = 'inbox', title, children }: { icon?: IconName; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center px-6 py-14 text-center">
      <span className="mb-3 flex h-11 w-11 items-center justify-center rounded-full bg-chalk-100 text-ink-mute">
        <Icon name={icon} size={22} />
      </span>
      <p className="font-display text-base font-semibold text-ink">{title}</p>
      {children && <div className="mt-1 max-w-sm text-sm text-ink-mute">{children}</div>}
    </div>
  );
}

export function Notice({ tone = 'yellow', children }: { tone?: 'yellow' | 'rose' | 'sky'; children: ReactNode }) {
  const styles = {
    yellow: 'border-hl-300 bg-hl-100/70 text-ink',
    rose: 'border-rose-200 bg-rose-50 text-rose-900',
    sky: 'border-sky-200 bg-sky-50 text-sky-900',
  };
  return <div className={cx('rounded-lg border px-4 py-3 text-sm', styles[tone])}>{children}</div>;
}

// ---------- tabs & pagination ----------

export function Tabs<T extends string>({
  value,
  onChange,
  tabs,
}: {
  value: T;
  onChange: (v: T) => void;
  tabs: Array<{ value: T; label: string; count?: number }>;
}) {
  return (
    <div role="tablist" className="flex gap-1 overflow-x-auto border-b border-chalk-200">
      {tabs.map((t) => (
        <button
          key={t.value}
          role="tab"
          type="button"
          aria-selected={value === t.value}
          onClick={() => onChange(t.value)}
          className={cx(
            '-mb-px flex items-center gap-1.5 whitespace-nowrap border-b-2 px-3 py-2.5 text-sm font-semibold transition-colors',
            value === t.value
              ? 'border-marker-600 text-marker-700'
              : 'border-transparent text-ink-mute hover:text-ink',
          )}
        >
          {t.label}
          {t.count !== undefined && (
            <span className="rounded-md bg-chalk-200 px-1.5 text-xs font-semibold text-ink-soft">{t.count}</span>
          )}
        </button>
      ))}
    </div>
  );
}

export function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-between border-t border-chalk-200 px-4 py-3 text-sm text-ink-mute">
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <div className="flex gap-2">
        <Button size="sm" icon="chevronLeft" disabled={page === 0} onClick={() => onChange(page - 1)}>
          Previous
        </Button>
        <Button size="sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
          Next
          <Icon name="chevronRight" size={15} />
        </Button>
      </div>
    </div>
  );
}

// ---------- modal ----------

export function Modal({
  open,
  onClose,
  title,
  children,
  footer,
  wide,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  // The native <dialog> gives us focus trapping, Escape to close and inert background for free.
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  if (!open) return null;
  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onMouseDown={(e) => {
        if (e.target === ref.current) onClose();
      }}
      className={cx(
        'm-auto max-h-[92vh] w-[calc(100%-1.5rem)] overflow-hidden rounded-2xl border border-chalk-200 bg-white p-0 shadow-pop',
        'backdrop:bg-ink/40',
        wide ? 'max-w-2xl' : 'max-w-lg',
      )}
    >
      <div className="flex max-h-[92vh] flex-col">
        <div className="flex items-center justify-between border-b border-chalk-200 px-5 py-4">
          <h2 className="font-display text-lg font-semibold text-ink">{title}</h2>
          <IconButton icon="x" label="Close" onClick={onClose} />
        </div>
        <div className="overflow-y-auto px-5 py-5">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-chalk-200 bg-chalk-50 px-5 py-3.5">{footer}</div>}
      </div>
    </dialog>
  );
}
