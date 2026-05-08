export function RecommendCard({ explanation }) {
  if (!explanation) return null;
  return (
    <div style={{
      padding: '12px 16px',
      background: 'rgba(255,255,255,0.08)',
      backdropFilter: 'blur(20px)',
      borderRadius: 14,
      border: '1px solid rgba(255,255,255,0.13)',
      marginTop: 16,
    }}>
      <div className="mono" style={{ fontSize: 10, letterSpacing: '.16em', opacity: 0.6, marginBottom: 8 }}>
        AI · WHY THIS SONG
      </div>
      <div style={{ fontFamily: 'var(--serif-cn)', fontSize: 14, lineHeight: 1.65, opacity: 0.9 }}>
        {explanation}
      </div>
    </div>
  );
}
