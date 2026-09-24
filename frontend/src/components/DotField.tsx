import { useEffect, useRef } from 'react';

interface DotFieldProps {
  dotRadius?: number;
  dotSpacing?: number;
  cursorRadius?: number;
  bulgeStrength?: number;
  glowRadius?: number;
}

export function DotField({
  dotRadius = 1.5,
  dotSpacing = 18,
  cursorRadius = 500,
  bulgeStrength = 2.4,
  glowRadius = 160,
}: DotFieldProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let frame = 0;
    let width = 0;
    let height = 0;
    let dpr = Math.min(window.devicePixelRatio || 1, 1.5);

    const pointer = { x: -1000, y: -1000, active: false };

    const resize = () => {
      width = window.innerWidth;
      height = window.innerHeight;
      dpr = Math.min(window.devicePixelRatio || 1, 1.5);

      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    const onPointerMove = (event: PointerEvent) => {
      pointer.x = event.clientX;
      pointer.y = event.clientY;
      pointer.active = true;
    };

    const onPointerLeave = () => {
      pointer.active = false;
    };

    const draw = () => {
      ctx.clearRect(0, 0, width, height);

      if (pointer.active) {
        const glow = ctx.createRadialGradient(
          pointer.x,
          pointer.y,
          0,
          pointer.x,
          pointer.y,
          glowRadius,
        );

        glow.addColorStop(0, 'rgba(126, 87, 194, 0.10)');
        glow.addColorStop(0.45, 'rgba(126, 87, 194, 0.045)');
        glow.addColorStop(1, 'rgba(126, 87, 194, 0)');

        ctx.fillStyle = glow;
        ctx.beginPath();
        ctx.arc(pointer.x, pointer.y, glowRadius, 0, Math.PI * 2);
        ctx.fill();
      }

      const step = Math.max(8, dotSpacing);
      const rows = Math.ceil(height / step);
      const cols = Math.ceil(width / step);

      for (let row = 0; row < rows; row += 1) {
        const y = step * 0.5 + row * step;

        for (let col = 0; col < cols; col += 1) {
          const x = step * 0.5 + col * step;
          const dx = x - pointer.x;
          const dy = y - pointer.y;
          const distance = Math.sqrt(dx * dx + dy * dy);

          let radius = dotRadius;
          let drawX = x;
          let drawY = y;

          if (pointer.active && distance < cursorRadius) {
            const influence = 1 - distance / cursorRadius;
            const eased = influence * influence;

            radius = dotRadius * (1 + eased * bulgeStrength);

            if (distance > 0.01) {
              const push = eased * 5;
              drawX += (dx / distance) * push;
              drawY += (dy / distance) * push;
            }
          }

          const area =
            (drawX / Math.max(width, 1) + drawY / Math.max(height, 1)) * 0.5;
          const alpha = 0.15 + area * 0.10;

          ctx.beginPath();
          ctx.arc(drawX, drawY, radius, 0, Math.PI * 2);

          const nearCursor = pointer.active && distance < cursorRadius * 0.55;
          ctx.fillStyle = `rgba(126, 87, 194, ${nearCursor ? Math.min(0.58, alpha + 0.22) : alpha})`;

          ctx.fill();
        }
      }

      frame = requestAnimationFrame(draw);
    };

    resize();
    draw();

    window.addEventListener('resize', resize);
    window.addEventListener('pointermove', onPointerMove, { passive: true });
    window.addEventListener('pointerleave', onPointerLeave);

    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', resize);
      window.removeEventListener('pointermove', onPointerMove);
      window.removeEventListener('pointerleave', onPointerLeave);
    };
  }, [dotRadius, dotSpacing, cursorRadius, bulgeStrength, glowRadius]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 z-0 h-full w-full"
    />
  );
}
