import { useEffect, useState } from 'react';
import { Button, Field, Modal, Textarea } from './ui';

/** Small dialog that asks for a short note, optionally required (for example when asking for changes). */
export function NoteModal({
  open,
  title,
  description,
  label,
  placeholder,
  required,
  confirmLabel,
  danger,
  busy,
  onSubmit,
  onClose,
}: {
  open: boolean;
  title: string;
  description?: string;
  label: string;
  placeholder?: string;
  required?: boolean;
  confirmLabel: string;
  danger?: boolean;
  busy?: boolean;
  onSubmit: (note: string) => void;
  onClose: () => void;
}) {
  const [note, setNote] = useState('');
  useEffect(() => {
    if (open) setNote('');
  }, [open]);

  const disabled = required && note.trim().length === 0;
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant={danger ? 'danger' : 'primary'} disabled={disabled} loading={busy} onClick={() => onSubmit(note.trim())}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {description && <p className="mb-4 text-sm text-ink-soft">{description}</p>}
      <Field label={label}>
        <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder={placeholder} maxLength={2000} autoFocus />
      </Field>
    </Modal>
  );
}
