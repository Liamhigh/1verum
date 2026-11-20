import { Router, type IRouter } from "express";
import { objectStorageClient } from "../lib/objectStorage";
import { parseDownloadRange } from "../lib/downloadRange";
import release from "../releases/android.json";

const router: IRouter = Router();
const path = `/releases/${release.filename}`;
const etag = `"${release.sha256}"`;

// Exact routes only: no user-supplied bucket paths, directory serving, or uploads.
router.get(`${path}.sha256`, (_req, res) => {
  res.set({
    "Content-Type": "text/plain; charset=utf-8",
    "Content-Disposition": `attachment; filename="${release.filename}.sha256"`,
    "X-Content-Type-Options": "nosniff",
    "Cache-Control": "public, max-age=3600",
  }).send(`${release.sha256}  ${release.filename}\n`);
});

router.get(path, async (req, res) => {
  const bucketId = process.env.DEFAULT_OBJECT_STORAGE_BUCKET_ID;
  if (!bucketId) {
    req.log.error("Release storage is not configured");
    res.status(503).json({ error: "Download temporarily unavailable. Please try again later." });
    return;
  }
  try {
    const file = objectStorageClient.bucket(bucketId).file(release.objectName);
    const [metadata] = await file.getMetadata();
    // Fail closed if a release was accidentally replaced in storage.
    if (Number(metadata.size) !== release.sizeBytes ||
        metadata.metadata?.sha256 !== release.sha256) {
      req.log.error("Stored release does not match the approved manifest");
      res.status(503).json({ error: "Download temporarily unavailable. Please try again later." });
      return;
    }
    res.set({
      "Content-Type": "application/vnd.android.package-archive",
      "Content-Disposition": `attachment; filename="${release.filename}"`,
      "X-Content-Type-Options": "nosniff",
      "Accept-Ranges": "bytes",
      "Cache-Control": "public, max-age=3600, no-transform",
      ETag: etag,
    });
    if (req.headers["if-none-match"] === etag && !req.headers.range) {
      res.status(304).end();
      return;
    }
    const useRange = !req.headers["if-range"] || req.headers["if-range"] === etag;
    const range = parseDownloadRange(useRange ? req.headers.range : undefined, release.sizeBytes);
    if (range === "invalid") {
      res.status(416).set("Content-Range", `bytes */${release.sizeBytes}`).end();
      return;
    }
    if (range) {
      res.status(206).set({
        "Content-Range": `bytes ${range.start}-${range.end}/${release.sizeBytes}`,
        "Content-Length": String(range.end - range.start + 1),
      });
    } else {
      res.set("Content-Length", String(release.sizeBytes));
    }
    if (req.method === "HEAD") {
      res.end();
      return;
    }
    // Pin the generation checked above to avoid replacement between stat and read.
    const stream = objectStorageClient.bucket(bucketId)
      .file(release.objectName, { generation: metadata.generation })
      .createReadStream(range ? { start: range.start, end: range.end } : {});
    stream.on("error", () => {
      req.log.error("Release download stream failed");
      if (res.headersSent) res.destroy();
      else {
        res.removeHeader("Content-Length");
        res.status(503).end();
      }
    });
    res.on("close", () => stream.destroy());
    stream.pipe(res);
  } catch {
    req.log.error("Release storage could not be reached");
    res.status(503).json({ error: "Download temporarily unavailable. Please try again later." });
  }
});

export default router;
