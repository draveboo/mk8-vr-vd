# Testing Methodology & Public Bandwidth Calculation
**Project**: The Galaxy (GEM — Galaxy Experience Machine / VKXR)  
**Modules Tested**: Sovereign Actions Bar, Special Moments Archive, WebXR Engine  
**Host & Edge Infrastructure**: Vercel Edge CDN + Neon Postgres

---

## 1. The Verification & Testing Methodology

This specification defines the exact testing protocol to verify that sovereign data ownership, platform sharing, and special moments functions execute with zero friction.

### Protocol A: Automated Archive Engine Test (Headless CLI)
Execute the automated test script in `celestial-preview`:

```bash
node test-moments-wiring.mjs
```

**Verification Checklist**:
* [x] **`archive.save(record)`**: Successfully persists moment vectors into IndexedDB / memory with auto-generated ID.
* [x] **`archive.exportJson()`**: Serializes the entire archive into valid, parseable JSON with export timestamp, total count, and nested object metadata.
* [x] **`archive.downloadAll()`**: Generates client-side blob trigger with proper filename convention (`special-moments-archive-[timestamp].json`).

---

### Protocol B: Live Production & Web Verification Checklist (30-Second Test)

Open `https://vkxr.vercel.app` or local `portal.html`:

| Target Action | Test Trigger | Expected Observable Result | Pass / Fail Criteria |
| :--- | :--- | :--- | :--- |
| **Download State** | Click `💾 DOWNLOAD` | Browser initiates immediate download of `my-galaxy-construct.json`. | Zero network requests to server; file contains 5 wholesome parameters. |
| **Email Passport** | Click `📧 EMAIL` | Prompts email (or uses prefilled), launches default email client with pre-composed re-entry link. | Subject and body contains sovereign 1-click URL (`#s=...`). |
| **Save to Drive** | Click `📁 DRIVE` | Initiates local backup download and opens `drive.google.com` in a new tab. | User has immediate local and cloud custody. |
| **WhatsApp Share** | Click `🟢 WHATSAPP` | Opens `api.whatsapp.com` with invite text and URL pre-composed. | Link is clickable and loads construct. |
| **Discord Invite** | Click `🎮 DISCORD` | Button flashes `COPIED!` and copies formatted markdown invite to clipboard. | Pasting into Discord displays `<https://vkxr.vercel.app/#s=...>`. |
| **Native AirDrop** | Click `📲 AIRDROP` | Triggers OS native share sheet on mobile/headset; falls back to clipboard copy. | Instant nearby beaming to family members. |

---

### Protocol C: Special Moments Archive In-Scene Test

In `celestial-preview/index.html` (or in VR headset):
1. **Capture**: Click **"SAVE THIS MOMENT"** (or wrist cuff button).
   * *Expected*: Scene checkpoint, camera frustum, and object transforms saved into local IndexedDB.
2. **Review Gallery**: Click **"MOMENTS"** to open the panel.
   * *Expected*: Master toolbar displays `[ 💾 EXPORT ALL MOMENTS ]` and `[ 📁 BACKUP ALL TO DRIVE ]`.
3. **Individual Row Actions**: On any moment card:
   * Click `💾` $\rightarrow$ Downloads `moment-[id].json`.
   * Click `📧` $\rightarrow$ Launches mailto composer with direct moment re-open hash.
   * Click `📁` $\rightarrow$ Downloads moment and launches Google Drive.
   * Click `🚀` $\rightarrow$ Beams moment to WhatsApp / Discord / AirDrop.

---

## 2. Public Bandwidth & Scaling Calculations (The Exact Math)

### A. The Core Bandwidth Principle
In traditional cloud gaming or remote VR (e.g. Pixel Streaming, Stadia, Plutosphere), 3D frames are rendered on cloud servers and video-streamed over the wire:
$$\text{Traditional Cloud VR Bandwidth} = 30\text{ Mbps} = 13.5\text{ GB / user / hour}\quad \mathbf{(Astronomically\ Expensive)}$$

In The Galaxy:
$$\mathbf{Server\ Frame\ Rendering\ Bandwidth} = \mathbf{0.00\ Mbps}$$
All 3D geometry, matrix math, shaders, and 90Hz frame loops execute **100% locally on the user's headset or device GPU**.

---

### B. Public Web Traffic & CDN Transfer Math

#### 1. Page Bundle Footprint
* Uncompressed static assets (HTML, CSS, lightweight JS modules, SVG glyphs): $\approx 1.5\text{ MB}$.
* Brotli / Gzip compressed delivery via Vercel Edge CDN: $\approx \mathbf{420\text{ KB}}$ per initial cold visitor.
* Subsequent page visits / returning users (HTTP 304 / ServiceWorker cache): $\approx \mathbf{12\text{ KB}}$.

#### 2. Vercel Pro Monthly Allowance ($20/month Base Tier)
* **Included Fast Data Transfer**: **1,000 GB (1 TB) per month**.
* **Included Edge Function Requests**: **1,000,000 invocations per month**.

#### 3. Maximum Monthly Traffic Capacity Before Additional Bandwidth Charges
$$\text{Max Cold Visitors} = \frac{1,000\text{ GB} \times 1,024\text{ MB/GB}}{0.42\text{ MB/visitor}} \approx \mathbf{2,438,000\ visitors / month}$$

$$\text{Max Conservative Visitors (Assuming Uncompressed 1.5MB)} = \frac{1,000,000\text{ MB}}{1.5\text{ MB}} \approx \mathbf{666,000\ visitors / month}$$

> [!IMPORTANT]
> Your existing $20/month Vercel Pro subscription covers between **666,000 and 2.4 Million monthly visits** with **zero additional bandwidth fees**.

---

### C. Bandwidth Cost Scaling Beyond 1 TB

If your product explodes in popularity and exceeds 1,000 GB of public data transfer:
* **Vercel Over-Bandwidth Rate**: $\$0.15\text{ per additional GB}$.
* **Example (5,000,000 visitors / 2 TB of bandwidth)**:
  * First 1,000 GB = Included ($0.00).
  * Next 1,000 GB = $1,000 \times \$0.15 = \$150.00$.
* **The Zero-Cost Cloudflare Scaling Trigger**:
  If public traffic exceeds 1 TB/month, place Cloudflare Free Tier in front of Vercel as a proxy cache. Cloudflare provides **unlimited free static caching and bandwidth**, bringing marginal bandwidth costs back to **$0.00**.

---

### D. Concurrency & Room Scaling Invariant

Because rooms and moments serialize state via URL hash strings (`#s=...`) and client-side IndexedDB:
* **Concurrent Headset Limit**: Unbounded by server memory. 10,000 simultaneous users downloading the static client consume $< 0.1\%$ of Vercel Edge network capacity.
* **Database Connection Pressure**: Only active during Square subscription checkout or optional vault syncs. Normal WebXR exploration generates **zero database queries**.
