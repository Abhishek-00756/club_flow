import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/lib/auth';
import { errorMessage } from '@/lib/api';
import { useEvents } from '@/lib/queries';
import { fmtDateTime, fmtTime } from '@/lib/format';
import type { EventItem } from '@/lib/types';
import { EventFormModal } from '@/components/EventFormModal';
import { Icon } from '@/components/Icon';
import { Badge, Button, Card, EmptyState, ErrorNote, Loading, PageHeader, Tabs } from '@/components/ui';

function EventCard({ event }: { event: EventItem }) {
  const start = new Date(event.startsAt);
  const past = new Date(event.endsAt).getTime() < Date.now();
  return (
    <Link to={`/events/${event.id}`}>
      <Card className="flex gap-4 p-4 transition-colors hover:border-chalk-300 sm:p-5">
        <div className="flex h-16 w-16 flex-none flex-col items-center justify-center rounded-xl bg-marker-50 text-marker-700">
          <span className="font-display text-2xl font-bold leading-none">{start.getDate()}</span>
          <span className="mt-1 text-xs font-semibold">{start.toLocaleString('en-IN', { month: 'short' })}</span>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h3 className="font-display text-lg font-semibold text-ink">{event.title}</h3>
            {past && <Badge>Finished</Badge>}
            {event.myParticipation && <Badge tone="green">You: {event.myParticipation.toLowerCase()}</Badge>}
          </div>
          <p className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-ink-mute">
            <span className="inline-flex items-center gap-1"><Icon name="clock" size={14} /> {fmtDateTime(event.startsAt)} to {fmtTime(event.endsAt)}</span>
            {event.location && <span className="inline-flex items-center gap-1"><Icon name="pin" size={14} /> {event.location}</span>}
          </p>
          <p className="mt-2 text-sm text-ink-soft">
            {event.attendeeCount} signed up, {event.volunteerCount} {event.volunteerCount === 1 ? 'volunteer' : 'volunteers'}, {event.taskCount} {event.taskCount === 1 ? 'task' : 'tasks'}
          </p>
        </div>
      </Card>
    </Link>
  );
}

export function EventsPage() {
  const { can } = useAuth();
  const [tab, setTab] = useState<'upcoming' | 'all'>('upcoming');
  const [creating, setCreating] = useState(false);
  const events = useEvents(tab === 'upcoming');

  return (
    <>
      <PageHeader
        title="Events"
        subtitle="Plan events, sign up as a volunteer and check in on the day."
        actions={can('EVENT_CREATE') ? <Button variant="primary" icon="plus" onClick={() => setCreating(true)}>New event</Button> : undefined}
      />
      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { value: 'upcoming', label: 'Upcoming' },
          { value: 'all', label: 'All events' },
        ]}
      />
      <div className="mt-5 space-y-3">
        {events.isLoading ? (
          <Loading />
        ) : events.isError ? (
          <ErrorNote message={errorMessage(events.error)} onRetry={() => events.refetch()} />
        ) : events.data && events.data.length > 0 ? (
          events.data.map((e) => <EventCard key={e.id} event={e} />)
        ) : (
          <Card>
            <EmptyState icon="calendar" title={tab === 'upcoming' ? 'No upcoming events' : 'No events yet'}>
              {can('EVENT_CREATE') ? 'Create an event, then generate its task checklist from a template.' : 'Events your club plans will show up here.'}
            </EmptyState>
          </Card>
        )}
      </div>
      <EventFormModal open={creating} onClose={() => setCreating(false)} />
    </>
  );
}
