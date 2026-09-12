import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../app/src/main/assets/web/app.js', import.meta.url), 'utf8');
function page() {
  const elements = new Map();
  const requests = [];
  const document = {
    getElementById(id) {
      if (!elements.has(id)) elements.set(id, { textContent: '', dataset: {}, hidden: false, disabled: false, value: 0, addEventListener() {}, click() {} });
      return elements.get(id);
    },
    addEventListener() {}
  };
  class XMLHttpRequest {
    constructor() { this.upload = {}; this.headers = {}; requests.push(this); }
    open(method, path) { this.method = method; this.path = path; }
    setRequestHeader(name, value) { this.headers[name] = value; }
    send(file) { this.file = file; }
    abort() { this.onabort(); }
  }
  const context = vm.createContext({ document, location: { host: '192.168.1.5:56321' }, XMLHttpRequest });
  vm.runInContext(source, context);
  return { context, requests, get: (id) => document.getElementById(id) };
}

test('raw upload has no installation request and waits for server acknowledgement', () => {
  const p = page();
  p.context.upload({ name: 'example.apk', size: 128 });
  const request = p.requests[0];
  assert.equal(request.method, 'POST');
  assert.equal(request.path, '/api/upload');
  assert.equal(request.headers['Content-Type'], 'application/octet-stream');
  request.upload.onprogress({ lengthComputable: true, loaded: 128, total: 128 });
  assert.equal(p.get('transfer').dataset.state, 'uploading');
  assert.match(p.get('status').textContent, /等待电视/);
  request.status = 201;
  request.responseText = '{"ok":true}';
  request.onload();
  assert.equal(p.get('transfer').dataset.state, 'success');
  assert.equal(p.requests.length, 1);
  assert.equal(p.get('choose').disabled, false);
});

test('rejects unsupported, empty and oversized files without network writes', () => {
  for (const file of [{ name: 'split.xapk', size: 10 }, { name: 'empty.apk', size: 0 }, { name: 'large.apk', size: 512 * 1024 * 1024 + 1 }]) {
    const p = page();
    p.context.upload(file);
    assert.equal(p.requests.length, 0);
    assert.equal(p.get('transfer').dataset.state, 'error');
  }
});

test('does not create a concurrent upload from the same page', () => {
  const p = page();
  p.context.upload({ name: 'one.apk', size: 8 });
  p.context.upload({ name: 'two.apk', size: 8 });
  assert.equal(p.requests.length, 1);
});

test('untrusted filenames remain text and are encoded in the request header', () => {
  const p = page();
  const name = '<img src=x onerror=alert(1)> 中文.apk';
  p.context.upload({ name, size: 8 });
  assert.equal(p.get('filename').textContent, name);
  assert.equal(p.requests[0].headers['X-File-Name'], encodeURIComponent(name));
  assert.equal(source.includes('innerHTML'), false);
});

test('server rejection is not reported as upload success', () => {
  const p = page();
  p.context.upload({ name: 'bad.apk', size: 8 });
  p.requests[0].status = 422;
  p.requests[0].responseText = '{"ok":false,"message":"无效 APK"}';
  p.requests[0].onload();
  assert.equal(p.get('transfer').dataset.state, 'error');
  assert.equal(p.get('status').textContent, '无效 APK');
});

test('connection loss and cancellation do not claim that a persisted APK was deleted', () => {
  for (const callback of ['onerror', 'onabort']) {
    const p = page();
    p.context.upload({ name: 'example.apk', size: 8 });
    p.requests[0][callback]();
    assert.equal(p.get('transfer').dataset.state, 'error');
    assert.match(p.get('status').textContent, /可能/);
  }
});
