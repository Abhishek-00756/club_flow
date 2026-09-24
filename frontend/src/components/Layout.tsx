import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/lib/auth';
import { useActiveClub } from '@/lib/club';
import { ROLE_LABEL } from '@/lib/format';
import { Icon, type IconName } from './Icon';
import { NotificationBell } from './NotificationBell';
import { Avatar, IconButton, Select, cx } from './ui';

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  /** Shown when the person holds any of these. Empty means everyone. */
  any?: string[];
  end?: boolean;
}

const GROUPS: Array<{ title?: string; items: NavItem[] }> = [
  {
    items: [
      { to: '/', label: 'Dashboard', icon: 'dashboard', end: true },
      { to: '/tasks', label: 'Tasks', icon: 'tasks', any: ['TASK_VIEW_ALL', 'TASK_VIEW_ASSIGNED'] },
      { to: '/events', label: 'Events', icon: 'calendar', any: ['EVENT_VIEW'] },
      { to: '/announcements', label: 'Announcements', icon: 'megaphone' },
    ],
  },
  {
    title: 'Manage',
    items: [
      { to: '/members', label: 'Members', icon: 'users', any: ['USER_VIEW'] },
      { to: '/automation', label: 'Templates and repeats', icon: 'repeat', any: ['TEMPLATE_MANAGE'] },
      { to: '/club', label: 'Club settings', icon: 'building', any: ['CLUB_UPDATE', 'DEPARTMENT_MANAGE', 'CLUB_MANAGE'] },
    ],
  },
  {
    title: 'Administration',
    items: [
      { to: '/roles', label: 'Roles and permissions', icon: 'shield', any: ['SYSTEM_CONFIG'] },
      { to: '/audit', label: 'Audit log', icon: 'scroll', any: ['AUDIT_VIEW'] },
      { to: '/system', label: 'Email delivery', icon: 'mail', any: ['SYSTEM_CONFIG'] },
    ],
  },
];

function Brand() {
  return (
    <Link to="/" className="flex items-center gap-2.5 px-2">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-marker-600">
        <svg width="20" height="20" viewBox="0 0 32 32" aria-hidden="true">
          <path d="M6 22c4 0 4.5-12 8.5-12S18 22 22 22s3-6 4-6" fill="none" stroke="#F4D954" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </span>
      <span className="font-display text-lg font-bold tracking-tight text-ink">ClubFlow</span>
    </Link>
  );
}

function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { canAny } = useAuth();
  return (
    <nav className="flex-1 space-y-5 overflow-y-auto px-3 py-4" aria-label="Main">
      {GROUPS.map((group, gi) => {
        const items = group.items.filter((i) => !i.any || canAny(...i.any));
        if (items.length === 0) return null;
        return (
          <div key={gi}>
            {group.title && <p className="mb-1.5 px-3 text-xs font-semibold text-ink-faint">{group.title}</p>}
            <ul className="space-y-0.5">
              {items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.end}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cx(
                        'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-semibold transition-colors',
                        isActive ? 'bg-marker-600 text-white' : 'text-ink-soft hover:bg-chalk-200',
                      )
                    }
                  >
                    <Icon name={item.icon} size={18} />
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        );
      })}
    </nav>
  );
}

function UserMenu() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  if (!user) return null;
  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex items-center gap-2 rounded-lg py-1 pl-1 pr-2 hover:bg-chalk-200"
      >
        <Avatar name={user.name} size={32} />
        <span className="hidden text-left leading-tight sm:block">
          <span className="block text-sm font-semibold text-ink">{user.name}</span>
          <span className="block text-xs text-ink-mute">{ROLE_LABEL[user.role]}</span>
        </span>
        <Icon name="chevronDown" size={15} className="text-ink-mute" />
      </button>
      {open && (
        <div role="menu" className="absolute right-0 top-11 z-40 w-56 overflow-hidden rounded-xl border border-chalk-200 bg-white py-1 shadow-pop">
          <div className="border-b border-chalk-200 px-4 py-2.5">
            <p className="truncate text-sm font-semibold">{user.name}</p>
            <p className="truncate text-xs text-ink-mute">{user.email}</p>
          </div>
          <button
            role="menuitem"
            type="button"
            className="flex w-full items-center gap-2.5 px-4 py-2.5 text-sm hover:bg-chalk-50"
            onClick={() => {
              setOpen(false);
              navigate('/settings');
            }}
          >
            <Icon name="user" size={16} /> Profile and security
          </button>
          <button
            role="menuitem"
            type="button"
            className="flex w-full items-center gap-2.5 px-4 py-2.5 text-sm hover:bg-chalk-50"
            onClick={() => {
              setOpen(false);
              void logout();
            }}
          >
            <Icon name="logout" size={16} /> Sign out
          </button>
        </div>
      )}
    </div>
  );
}

export function Layout() {
  const [drawer, setDrawer] = useState(false);
  const { user } = useAuth();
  const { club, clubs, isSuper, clubId, setClubId } = useActiveClub();
  const location = useLocation();

  useEffect(() => setDrawer(false), [location.pathname]);

  return (
    <div className="min-h-screen lg:pl-64">
      {/* desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-chalk-200 bg-chalk-50 lg:flex">
        <div className="flex h-16 items-center border-b border-chalk-200">
          <Brand />
        </div>
        <SidebarNav />
        {club && !isSuper && (
          <div className="border-t border-chalk-200 px-5 py-3 text-xs text-ink-mute">
            <span className="block font-semibold text-ink-soft">{club.name}</span>
            {user?.departmentName ? `${user.departmentName} department` : 'No department yet'}
          </div>
        )}
      </aside>

      {/* mobile drawer */}
      {drawer && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-ink/40" onClick={() => setDrawer(false)} />
          <aside className="absolute inset-y-0 left-0 flex w-72 max-w-[85vw] flex-col bg-chalk-50 shadow-pop">
            <div className="flex h-16 items-center justify-between border-b border-chalk-200 pr-3">
              <Brand />
              <IconButton icon="x" label="Close menu" onClick={() => setDrawer(false)} />
            </div>
            <SidebarNav onNavigate={() => setDrawer(false)} />
          </aside>
        </div>
      )}

      <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-chalk-200 bg-chalk-100/95 px-4 backdrop-blur sm:px-6">
        <IconButton icon="menu" label="Open menu" className="lg:hidden" onClick={() => setDrawer(true)} />
        <div className="min-w-0 flex-1">
          {isSuper ? (
            clubs.length > 0 && (
              <Select
                aria-label="Club"
                value={clubId ?? ''}
                onChange={(e) => setClubId(e.target.value)}
                className="h-9 max-w-[16rem] font-semibold"
              >
                {clubs.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
              </Select>
            )
          ) : (
            <p className="truncate font-display text-base font-semibold text-ink lg:hidden">{club?.name}</p>
          )}
        </div>
        <NotificationBell />
        <UserMenu />
      </header>

      <main className="mx-auto w-full max-w-6xl px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  );
}
