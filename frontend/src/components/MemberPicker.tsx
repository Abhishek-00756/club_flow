import { useMemo, useState } from 'react';
import type { User } from '@/lib/types';
import { Avatar, Input, cx } from './ui';

/** A filterable checklist of club members. */
export function MemberPicker({
  members,
  selected,
  onChange,
  loading,
}: {
  members: User[];
  selected: string[];
  onChange: (ids: string[]) => void;
  loading?: boolean;
}) {
  const [filter, setFilter] = useState('');
  const shown = useMemo(() => {
    const q = filter.trim().toLowerCase();
    return q ? members.filter((m) => m.name.toLowerCase().includes(q) || (m.departmentName ?? '').toLowerCase().includes(q)) : members;
  }, [members, filter]);

  const toggle = (id: string) =>
    onChange(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id]);

  return (
    <div className="rounded-lg border border-chalk-300">
      <div className="border-b border-chalk-200 p-2">
        <Input value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="Filter by name or department" className="h-9" aria-label="Filter members" />
      </div>
      <ul className="max-h-48 overflow-y-auto">
        {loading && <li className="px-3 py-3 text-sm text-ink-mute">Loading members</li>}
        {!loading && shown.length === 0 && <li className="px-3 py-3 text-sm text-ink-mute">No one matches.</li>}
        {shown.map((m) => {
          const on = selected.includes(m.id);
          return (
            <li key={m.id}>
              <label className={cx('flex cursor-pointer items-center gap-3 px-3 py-2 hover:bg-chalk-50', on && 'bg-marker-50')}>
                <input type="checkbox" checked={on} onChange={() => toggle(m.id)} className="h-4 w-4 accent-marker-600" />
                <Avatar name={m.name} size={24} />
                <span className="min-w-0 flex-1 truncate text-sm font-semibold text-ink">{m.name}</span>
                {m.departmentName && <span className="text-xs text-ink-mute">{m.departmentName}</span>}
              </label>
            </li>
          );
        })}
      </ul>
      <p className="border-t border-chalk-200 px-3 py-1.5 text-xs text-ink-mute">{selected.length} selected</p>
    </div>
  );
}
