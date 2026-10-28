/**
 * Sovereign Actions: Simplest & Strongest Mechanics for Share and Data Ownership
 */
export function downloadConstructState(state, filename = null) {
  const payload = {
    app: 'The Galaxy (GEM)',
    version: '1.0',
    timestamp: new Date().toISOString(),
    matrix: state,
    url: window.location.href.split('#')[0] + '#' + encodeURIComponent(JSON.stringify(state))
  };
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename || `my-galaxy-construct-${Date.now()}.json`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export function emailConstructBackup(state, userEmail = '') {
  const shareUrl = window.location.href.split('#')[0] + '#' + encodeURIComponent(JSON.stringify(state));
  const subject = encodeURIComponent('My Galaxy Construct Passport & 3D World Link');
  const body = encodeURIComponent(
    `Here is your sovereign 3D Construct in The Galaxy!\n\n` +
    `1-Click Re-Entry Link:\n${shareUrl}\n\n` +
    `Created: ${new Date().toLocaleString()}\n` +
    `Keep this email safe. Your creations belong to you forever.`
  );
  window.open(`mailto:${userEmail}?subject=${subject}&body=${body}`, '_blank');
}

export function saveToGoogleDrive(state) {
  downloadConstructState(state, 'The-Galaxy-Construct-Backup.json');
  setTimeout(() => {
    window.open('https://drive.google.com/drive/u/0/my-drive', '_blank');
  }, 300);
}

export function shareToWhatsApp(state) {
  const shareUrl = window.location.href.split('#')[0] + '#' + encodeURIComponent(JSON.stringify(state));
  const text = encodeURIComponent(`Come check out my 3D Construct in The Galaxy! No install needed: ${shareUrl}`);
  window.open(`https://api.whatsapp.com/send?text=${text}`, '_blank');
}

export function shareToX(state) {
  const shareUrl = window.location.href.split('#')[0] + '#' + encodeURIComponent(JSON.stringify(state));
  const text = encodeURIComponent(`Exploring my sovereign 3D world in The Galaxy (GEM)! ✦ Enter here:`);
  window.open(`https://twitter.com/intent/tweet?text=${text}&url=${encodeURIComponent(shareUrl)}`, '_blank');
}

export async function copyDiscordInvite(state, onCopySuccess = null) {
  const shareUrl = window.location.href.split('#')[0] + '#' + encodeURIComponent(JSON.stringify(state));
  const text = `**✦ The Galaxy Construct Invite**\nCome explore my 3D space:\n<${shareUrl}>`;
  try {
    await navigator.clipboard.writeText(text);
    if (onCopySuccess) onCopySuccess();
  } catch {
    prompt('Copy your Discord invite link:', text);
  }
}

export async function nativeShare(state) {
  const shareUrl = window.location.href.split('#')[0] + '#' + encodeURIComponent(JSON.stringify(state));
  if (navigator.share) {
    try {
      await navigator.share({
        title: 'The Galaxy Construct',
        text: 'Come check out my sovereign 3D space!',
        url: shareUrl
      });
      return true;
    } catch (err) {
      if (err.name === 'AbortError') return false;
    }
  }
  await navigator.clipboard.writeText(shareUrl);
  return false;
}
