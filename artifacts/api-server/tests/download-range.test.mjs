import { test } from "node:test";
import assert from "node:assert/strict";
import { parseDownloadRange } from "../src/lib/downloadRange.ts";

test("whole file and resumable byte ranges", () => {
  assert.equal(parseDownloadRange(undefined, 100), undefined);
  assert.deepEqual(parseDownloadRange("bytes=0-9", 100), { start: 0, end: 9 });
  assert.deepEqual(parseDownloadRange("bytes=90-", 100), { start: 90, end: 99 });
  assert.deepEqual(parseDownloadRange("bytes=-10", 100), { start: 90, end: 99 });
  assert.deepEqual(parseDownloadRange("bytes=0-999", 100), { start: 0, end: 99 });
  assert.deepEqual(parseDownloadRange("bytes=-999", 100), { start: 0, end: 99 });
});

test("invalid, unsatisfiable and multi-ranges are rejected", () => {
  for (const value of ["bytes=", "bytes=-", "bytes=-0", "bytes=100-", "bytes=9-1",
    "bytes=0-1,5-9", "bytes=a-z", "items=0-1", "bytes=9007199254740992-"]) {
    assert.equal(parseDownloadRange(value, 100), "invalid", value);
  }
});
