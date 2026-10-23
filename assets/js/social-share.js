(function () {
  'use strict';
  document.querySelectorAll('[data-share-set]').forEach(function (group) {
    var url = group.dataset.shareUrl || location.href;
    var title = group.dataset.shareTitle || document.title;
    var text = group.dataset.shareText || title;
    var encodedUrl = encodeURIComponent(url);
    var encodedText = encodeURIComponent(text);
    var targets = {
      x: 'https://twitter.com/intent/tweet?text=' + encodedText + '&url=' + encodedUrl,
      facebook: 'https://www.facebook.com/sharer/sharer.php?u=' + encodedUrl,
      linkedin: 'https://www.linkedin.com/sharing/share-offsite/?url=' + encodedUrl,
      reddit: 'https://www.reddit.com/submit?url=' + encodedUrl + '&title=' + encodeURIComponent(title),
      whatsapp: 'https://api.whatsapp.com/send?text=' + encodedText + '%20' + encodedUrl
    };
    group.querySelectorAll('[data-share-service]').forEach(function (link) {
      link.href = targets[link.dataset.shareService] || url;
    });
    group.querySelector('[data-share-copy]')?.addEventListener('click', async function (event) {
      try {
        await navigator.clipboard.writeText(url);
        event.currentTarget.textContent = 'Link copied';
      } catch {
        event.currentTarget.textContent = 'Copy from address bar';
      }
    });
    group.querySelector('[data-share-native]')?.addEventListener('click', async function (event) {
      if (navigator.share) {
        try { await navigator.share({title: title, text: text, url: url}); }
        catch (error) { if (error.name !== 'AbortError') event.currentTarget.textContent = 'Share unavailable'; }
      } else {
        try { await navigator.clipboard.writeText(url); event.currentTarget.textContent = 'Link copied'; }
        catch { event.currentTarget.textContent = 'Copy from address bar'; }
      }
    });
  });
})();
