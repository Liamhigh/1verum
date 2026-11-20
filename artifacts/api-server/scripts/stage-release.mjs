/**
 * Maintainer-only staging, never part of a web build/start command.
 * First verify the APK with Android apksigner, then run:
 * node --experimental-strip-types artifacts/api-server/scripts/stage-release.mjs <apk>
 */
import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { readFile, stat } from "node:fs/promises";
import { objectStorageClient } from "../src/lib/objectStorage.ts";

const manifest = JSON.parse(await readFile(new URL("../src/releases/android.json", import.meta.url)));
const source = process.argv[2];
if (!source) throw new Error("Provide a locally signature-verified release APK.");
if (!process.env.DEFAULT_OBJECT_STORAGE_BUCKET_ID) throw new Error("Release storage is not configured.");
const digest = createHash("sha256");
for await (const chunk of createReadStream(source)) digest.update(chunk);
const checksum = digest.digest("hex");
if (checksum !== manifest.sha256 || (await stat(source)).size !== manifest.sizeBytes) {
  throw new Error("APK does not match the approved release manifest.");
}
const bucket = objectStorageClient.bucket(process.env.DEFAULT_OBJECT_STORAGE_BUCKET_ID);
const object = bucket.file(manifest.objectName);
const [exists] = await object.exists();
if (!exists) {
  await bucket.upload(source, {
    destination: manifest.objectName,
    preconditionOpts: { ifGenerationMatch: 0 },
    metadata: {
      contentType: "application/vnd.android.package-archive",
      cacheControl: "public, max-age=3600, no-transform",
      contentDisposition: `attachment; filename="${manifest.filename}"`,
      metadata: { sha256: manifest.sha256 },
    },
  });
}
const [metadata] = await object.getMetadata();
if (metadata.metadata?.sha256 !== checksum || Number(metadata.size) !== manifest.sizeBytes) {
  throw new Error("Stored object does not match approved release.");
}
// Check bytes from durable storage, not just developer-supplied object metadata.
const remoteDigest = createHash("sha256");
for await (const chunk of object.createReadStream()) remoteDigest.update(chunk);
if (remoteDigest.digest("hex") !== checksum) throw new Error("Stored APK checksum mismatch.");
console.log(`Release staged and verified: ${manifest.filename}, ${manifest.sizeBytes} bytes, SHA-256 ${checksum}`);
