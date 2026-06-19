import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  stages: [
    { duration: "30s", target: 20 },
    { duration: "1m", target: 50 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_duration: ["p(95)<500", "p(99)<900"],
    http_req_failed: ["rate<0.01"],
  },
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8081";
const TOKEN = __ENV.TOKEN || "";

export default function () {
  const params = {
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${TOKEN}`,
    },
  };

  const clienteId = `cliente-${Math.random().toString(36).slice(2)}`;
  const res = http.post(
    `${BASE_URL}/api/v1/pedidos`,
    JSON.stringify({ clienteId }),
    params
  );

  check(res, { "status 201": (r) => r.status === 201 });
  sleep(1);
}
