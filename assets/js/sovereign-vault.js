/**
 * Client-Side Sovereign Vault Bridge
 * Automatically synchronizes calibrated matrix values to the cryptographic vault
 * and provides one-click restore.
 */
export const VAULT_STORAGE_KEY = 'sovereign_vault_credentials';
export const MATRIX_STORAGE_KEY = 'celestial_user_matrix_calibration';

export class SovereignVaultClient {
  constructor(endpoint = '/api/vault') {
    this.endpoint = endpoint;
  }

  getCredentials() {
    try {
      const raw = localStorage.getItem(VAULT_STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  saveCredentials(email, key) {
    localStorage.setItem(VAULT_STORAGE_KEY, JSON.stringify({ email, key }));
  }

  /**
   * Backup current local matrix values + optional Square payment ID
   */
  async backup(email, paymentId = null) {
    const creds = this.getCredentials() || {};
    const targetEmail = email || creds.email;
    if (!targetEmail) throw new Error('Email required for vault backup.');

    let localMatrix = {};
    try {
      const rawMatrix = localStorage.getItem(MATRIX_STORAGE_KEY);
      if (rawMatrix) localMatrix = JSON.parse(rawMatrix);
    } catch {}

    const res = await fetch(this.endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: targetEmail,
        key: creds.key || null,
        matrixState: localMatrix,
        paymentId: paymentId
      })
    });

    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Backup failed.');

    this.saveCredentials(data.email, data.key);
    return data;
  }

  /**
   * Restore matrix values to local construct from Email + Cryptographic Key
   */
  async restore(email, key) {
    const params = new URLSearchParams({ email, key });
    const res = await fetch(`${this.endpoint}?${params.toString()}`);
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Restore failed.');

    // Save restored matrix directly to localStorage so WebXR construct immediately activates it
    if (data.matrixState && Object.keys(data.matrixState).length > 0) {
      localStorage.setItem(MATRIX_STORAGE_KEY, JSON.stringify(data.matrixState));
      // Dispatch storage event for live construct synchronization
      window.dispatchEvent(new Event('storage'));
    }

    this.saveCredentials(email, key);
    return data;
  }
}
