import { test } from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"

test("frontend proxy sanitizes forwarded headers before the backend trusts them", () => {
  const nginx = readFileSync(
    new URL("../nginx/default.conf.template", import.meta.url),
    "utf8",
  )

  assert.match(nginx, /proxy_set_header Forwarded "";/)
  assert.match(nginx, /proxy_set_header X-Real-IP \$http_x_real_ip;/)
  assert.match(nginx, /proxy_set_header X-Forwarded-For \$http_x_real_ip;/)
  assert.match(nginx, /proxy_set_header X-Forwarded-Proto \$http_x_forwarded_proto;/)
  assert.doesNotMatch(nginx, /\$proxy_add_x_forwarded_for|\$scheme;/)
})
