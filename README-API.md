# Headless HTTP API

Start server (no UI):

```bash
java -jar reflowcontroller-0.1.jar --api --port=8080
```

### Endpoints

- `GET /api/status` → connection + live readings, includes `mode`
- `GET /api/ports` → serial ports
- `POST /api/connect` `{ "port": "COM3" }`
- `POST /api/disconnect`
- **Manual control**
  - `POST /api/manual/start` *(optionally with `{ "temp": 150, "intensity": 0.3 }`)*
  - `POST /api/manual/set` `{ "temp": 150.0, "intensity": 0.35 }`
  - `POST /api/manual/stop`
- **Profiles**
  - `GET /api/profiles` → profiles from `/profiles/*.json`
  - `POST /api/profiles/validate` `{ "profile": { ... } }` → `{"valid":true|false,"issues":[...]}`
  - `POST /api/profiles/save` `{ "profile": { ... }, "fileName": "MyProfile.json" }`
  - `POST /api/start` with either:
    - `{ "profileName": "SAC305" }` **or**
    - `{ "profile": { ... full profile JSON ... } }`
  - `POST /api/start-inline` `{ "profile": { ... } }` (alias)
- `POST /api/stop`
- `GET /api/logs` → both message + state logs
- `GET /api/logs/messages`
- `GET /api/logs/states`
- `DELETE /api/logs` → clear logs

### Profile JSON shape (fixed)

```json
{
  "name": "Lead-free SAC305",
  "preheat": { "target_temperature": 150, "intensity": 0.30, "hold_for": 90, "time": 120 },
  "soak":    { "target_temperature": 180, "intensity": 0.40, "hold_for": 90, "time": 120 },
  "reflow":  { "target_temperature": 240, "intensity": 0.75, "hold_for": 40, "time":  90 }
}
```
- `target_temperature`: 0..300 (°C)
- `intensity`: 0.0..1.0
- `hold_for`: seconds to maintain target
- `time`: total phase length in seconds (`0` = unlimited)

### Curl examples

```bash
# Inline start (with your schema)
curl -X POST http://localhost:8080/api/start -H 'Content-Type: application/json' \
  -d '{
        "profile": {
          "name": "Quick-Leadfree",
          "preheat": { "target_temperature": 150, "intensity": 0.30, "hold_for": 90, "time": 120 },
          "soak":    { "target_temperature": 180, "intensity": 0.40, "hold_for": 90, "time": 120 },
          "reflow":  { "target_temperature": 240, "intensity":  0.75, "hold_for": 40, "time":  90 }
        }
      }'

# Validate then save profile on the device
curl -X POST http://localhost:8080/api/profiles/validate -H 'Content-Type: application/json' \
  -d '{"profile": {"name":"Quick-Leadfree","preheat":{"target_temperature":150,"intensity":0.3,"hold_for":60,"time":120},"soak":{"target_temperature":180,"intensity":0.4,"hold_for":60,"time":120},"reflow":{"target_temperature":240,"intensity":0.75,"hold_for":40,"time":90}}}'

curl -X POST http://localhost:8080/api/profiles/save -H 'Content-Type: application/json' \
  -d '{"profile": {"name":"Quick-Leadfree","preheat":{"target_temperature":150,"intensity":0.3,"hold_for":60,"time":120},"soak":{"target_temperature":180,"intensity":0.4,"hold_for":60,"time":120},"reflow":{"target_temperature":240,"intensity":0.75,"hold_for":40,"time":90}}, "fileName":"Quick-Leadfree.json"}'
```
