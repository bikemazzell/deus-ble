# Advertisement format

This profile describes fields observed through static analysis of two Android receivers. It is not a manufacturer specification. Hardware compatibility remains unverified.

The parser reads the entire BLE advertisement, not a manufacturer-data subsection. The minimum length is 21 bytes. The first three bytes must equal `02 01 05`. These bytes form a common Bluetooth flags structure and do not establish detector identity.

| Byte offset | Field | Interpretation |
| --- | --- | --- |
| 0–2 | Signature | `02 01 05` |
| 3–16 | Unknown | Preserve without interpretation |
| 17 | Target ID | Unsigned value from 0 through 99 |
| 18 | Unknown | Preserve without interpretation |
| 19 | Soil | `90 - unsignedValue / 4`, integer division |
| 20 | Frame and mode | Full unsigned byte; up to 127 is PUSH, otherwise AUTO |
| 21 onward | Unknown trailing data | Preserve without interpretation |

The soil calculation yields 27 through 90. Its physical calibration is unknown. RSSI comes from the Android scan result. It measures received radio strength, not buried target depth.

The adapter uses advertised service UUID `00001812-0000-1000-8000-00805f9b34fb`. It excludes names containing `RTKDeus`. The user selects a candidate address before detection delivery starts. This profile does not establish any detector control command.

A repeated frame value can represent a repeated broadcast. The library suppresses consecutive equal frames per address and exposes raw observations separately. A one-byte frame cannot establish event identity after arbitrary packet loss or wraparound. Consumers must retain observation times and raw data.

## Hardware acceptance

Record the detector firmware, phone model, and Android version. Capture real advertisements while changing target and soil conditions. Compare decoded values with the detector display. Test PUSH and AUTO modes, repeated sweeps, range loss, and screen-off reception. Retain the raw captures and document disagreements before claiming support.
