import { agentFetch } from "./agentAuth";

export type DirectUploadTicket = {
  uploadId: string;
  uploadUrl: string;
  objectKey: string;
  expiresAt: number;
  credentials: { tmpSecretId: string; tmpSecretKey: string; token: string };
};

export type DirectUploadedAsset = {
  assetId: string;
  imageUrl: string;
  storageProvider: string;
  modelAccessible: boolean;
  size: number;
};

function encode(value: string) { return encodeURIComponent(value).replace(/\*/g, "%2A").replace(/%7E/g, "~"); }
function hex(buffer: ArrayBuffer) { return Array.from(new Uint8Array(buffer)).map((item) => item.toString(16).padStart(2, "0")).join(""); }
async function hmacSha1(key: string, value: string) { const cryptoKey = await crypto.subtle.importKey("raw", new TextEncoder().encode(key), { name: "HMAC", hash: "SHA-1" }, false, ["sign"]); return hex(await crypto.subtle.sign("HMAC", cryptoKey, new TextEncoder().encode(value))); }

async function signCosPut(ticket: DirectUploadTicket, contentType: string) {
  const url = new URL(ticket.uploadUrl);
  const now = Math.floor(Date.now() / 1000);
  const keyTime = `${now};${Math.min(ticket.expiresAt, now + 900)}`;
  const headerList = "content-type;host;x-cos-security-token";
  const canonicalHeaders = `content-type=${encode(contentType.toLowerCase())}&host=${encode(url.host.toLowerCase())}&x-cos-security-token=${encode(ticket.credentials.token)}`;
  const httpString = `put\n/${ticket.objectKey}\n\n${canonicalHeaders}\n`;
  const digest = await crypto.subtle.digest("SHA-1", new TextEncoder().encode(httpString));
  const stringToSign = `sha1\n${keyTime}\n${hex(digest)}\n`;
  const signature = await hmacSha1(await hmacSha1(ticket.credentials.tmpSecretKey, keyTime), stringToSign);
  return `q-sign-algorithm=sha1&q-ak=${encode(ticket.credentials.tmpSecretId)}&q-sign-time=${keyTime}&q-key-time=${keyTime}&q-header-list=${headerList}&q-url-param-list=&q-signature=${signature}`;
}

async function uploadImageViaServer(apiBaseUrl: string, sessionId: string, file: File): Promise<DirectUploadedAsset> {
  const form = new FormData();
  form.append("file", file);
  form.append("sessionId", sessionId);
  const response = await agentFetch(apiBaseUrl + "/api/v1/agent/media/images", { method: "POST", body: form });
  if (!response.ok) throw new Error(`Server upload fallback failed: ${response.status}`);
  return await response.json() as DirectUploadedAsset;
}

/** The authorization header is sent only to the JendaAgent API, never to the COS PUT URL. */
export async function uploadImageDirect(apiBaseUrl: string, sessionId: string, file: File): Promise<DirectUploadedAsset> {
  // Use server transport only when explicitly disabled. Normally the browser uploads
  // through a CORS-approved STS ticket, then falls back to the authenticated backend.
  if (process.env.NEXT_PUBLIC_AGENT_COS_DIRECT_UPLOAD === "false") {
    return uploadImageViaServer(apiBaseUrl, sessionId, file);
  }
  try {
    const ticketResponse = await agentFetch(apiBaseUrl + "/api/v1/agent/media/direct/tickets", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId, fileName: file.name, mediaType: file.type, size: file.size }),
    });
    if (!ticketResponse.ok) throw new Error(`STS ticket request failed: ${ticketResponse.status}`);
    const ticket = await ticketResponse.json() as DirectUploadTicket;
    const cosResponse = await fetch(ticket.uploadUrl, { method: "PUT", headers: { "Content-Type": file.type, "x-cos-security-token": ticket.credentials.token, Authorization: await signCosPut(ticket, file.type) }, body: file });
    if (!cosResponse.ok) throw new Error(`COS direct upload failed: ${cosResponse.status}`);
    const completeResponse = await agentFetch(apiBaseUrl + "/api/v1/agent/media/direct/complete", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ uploadId: ticket.uploadId }) });
    if (!completeResponse.ok) throw new Error(`Upload verification failed: ${completeResponse.status}`);
    return await completeResponse.json() as DirectUploadedAsset;
  } catch (directError) {
    const message = directError instanceof Error ? directError.message : "Direct upload failed";
    // Authentication errors are not transport failures. agentFetch already retried a token refresh.
    if (message.includes(": 401")) throw new Error("Login expired. Please sign in again before uploading.");
    console.warn("COS direct upload failed; using backend upload fallback.", directError);
    try {
      return await uploadImageViaServer(apiBaseUrl, sessionId, file);
    } catch (fallbackError) {
      const fallbackMessage = fallbackError instanceof Error ? fallbackError.message : "Server upload fallback failed";
      throw new Error(`Image upload failed. Direct upload: ${message}. Fallback: ${fallbackMessage}`);
    }
  }
}
