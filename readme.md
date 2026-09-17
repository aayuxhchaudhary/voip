## Call Initiation

1. **INVITE** — Caller initiates the call.
2. **100 Trying** — Request received and being processed. If the request is invalid or fails, an appropriate error response may be returned.
3. **183 Session Progress** — Optional progress response.
4. **180 Ringing** — Callee's phone starts ringing.
5. **200 OK** — Callee accepts the call and sends the SDP answer.
6. **ACK** — Caller confirms the `200 OK`.
7. **RTP** — Audio starts flowing between caller and callee.

### SIP Server

The SIP server handles SIP signaling and routes the call between the caller and callee.

### Multiple Calls

If **20 calls are inputted, all 20 should be connected**.

Each call must have its own unique `Call-ID`, transaction identifiers, call state, and RTP session.

### Caller / Callee

```text
Caller [From] = A
Callee [To]   = B
```

### ANI

**ANI (Automatic Number Identification)** = Caller / originating number.

```text
ANI = A number
```

---

## SIP Headers

### 1. Application Layer (Java / SIP)

```sip
INVITE sip:userB@127.0.0.1:5060 SIP/2.0
Via: SIP/2.0/UDP 127.0.0.1:5070;branch=z9hG4bK-abc123
Max-Forwards: 70
From: <sip:1001@127.0.0.1:5070>;tag=caller123
To: <sip:userB@127.0.0.1:5060>
Call-ID: 5b3009@127.0.0.1
CSeq: 1 INVITE
Contact: <sip:1001@127.0.0.1:5070>
Content-Type: application/sdp
Content-Length: 150

v=0
o=1001 123456 123456 IN IP4 127.0.0.1
s=-
c=IN IP4 127.0.0.1
t=0 0
m=audio 8000 RTP/AVP 0
a=rtpmap:0 PCMU/8000
```

**Important headers:**

| Header           | Purpose                                               |
| ---------------- | ----------------------------------------------------- |
| `Via`            | Identifies the transport path and transaction branch. |
| `Max-Forwards`   | Prevents routing loops.                               |
| `From`           | Identifies the caller.                                |
| `To`             | Identifies the callee.                                |
| `Call-ID`        | Unique identifier for the call.                       |
| `CSeq`           | Sequence number and SIP method.                       |
| `Contact`        | Direct SIP address of the caller.                     |
| `Content-Type`   | Indicates SDP body.                                   |
| `Content-Length` | Length of the message body.                           |

**Empty / absent fields in the initial INVITE:**

* `To` tag — Usually absent; added by the callee in the response.
* `Authorization` — Absent unless authentication is required.
* `Route` — May be absent.
* `Record-Route` — Usually absent in the initial request.

---

### 2. Transport Layer (UDP)

```text
Source Port:      5070
Destination Port: 5060
Payload:          SIP Data Bytes
```

The SIP message is converted into bytes and placed inside a UDP datagram.

---

### 3. Network Layer (IP)

```text
Source IP:      127.0.0.1
Destination IP: 127.0.0.1
Payload:        UDP Datagram
```

The UDP datagram is placed inside an IP packet.

---

### 4. Destination

The operating system delivers the packet to the process listening on UDP port `5060`, such as a SIP server, PBX, or softphone.

---

## SIP Packet Flow

```text
PC A (Your App)                                  PC B (Callee / Softphone)
IP: 192.168.1.10                                 IP: 192.168.1.20
Port: 5070                                       Port: 5060
      │                                                 │
      │ 1. INVITE                                       │
      ├────────────────────────────────────────────────►│
      │                                                 │
      │ 2. 100 Trying                                   │
      │◄────────────────────────────────────────────────┤
      │                                                 │
      │ 3. 183 Session Progress (Optional)              │
      │◄────────────────────────────────────────────────┤
      │                                                 │
      │ 4. 180 Ringing                                  │
      │◄────────────────────────────────────────────────┤
      │                                                 │
      │       User on PC B answers the call              │
      │                                                 │
      │ 5. 200 OK                                       │
      │◄────────────────────────────────────────────────┤
      │                                                 │
      │ 6. ACK                                          │
      ├────────────────────────────────────────────────►│
      │                                                 │
      │════════════ RTP Audio Stream ═══════════════════│
```

---

## SIP Three-Way Exchange

```text
1. Caller ───────── INVITE (Offer) ─────────► Callee
2. Caller ◄──────── 200 OK (Answer) ──────── Callee
3. Caller ───────── ACK (Confirm) ──────────► Callee
```

**Note:** `183 Session Progress` and `180 Ringing` may occur between `INVITE` and `200 OK`. They are not part of the simplified three-way exchange.

---

## RTP Audio

After the SIP call is established, the actual audio is transmitted using RTP.

```text
Caller RTP ─────────────► Callee RTP
Caller RTP ◄───────────── Callee RTP
```

Example:

```text
Caller RTP IP:   192.168.1.10
Caller RTP Port: 8000

Callee RTP IP:   192.168.1.20
Callee RTP Port: 9000

Codec: PCMU / 8000 Hz
```
