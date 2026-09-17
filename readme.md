Call inititate - 1st packet - invite 
                 2nd - Try       //If wrong error
                 3rd- If success: Session progress   
                 4th - Ringing
                 5th - connection Establish
              
SIP server


If 20 call is inputted, 20 should be connected
caller [from] A- callee [to] B
ANI zone - A no.


SIP Headers


1. Application Layer (Java / SIP)
   ┌──────────────────────────────────────────────────────────┐
   │ INVITE sip:userB@127.0.0.1:5060 SIP/2.0                  │
   │ Via: SIP/2.0/UDP 127.0.0.1:5070;branch=z9hG4bK-...       │
   │ From: <sip:1001@127.0.0.1:5070>;tag=...                  │
   │ To: <sip:userB@127.0.0.1:5060>                           │
   │ Call-ID: 5b3009...                                       │
   │ [SDP Audio Body: PCMU/8000 on port 8000]                 │
   └────────────────────────────┬─────────────────────────────┘
                                │ Transformed into raw UTF-8 bytes
                                ▼
2. Transport Layer (UDP)
   ┌────────────────────────────┬─────────────────────────────┐
   │ Source Port: 5070          │ Destination Port: 5060      │ + [SIP Data Bytes]
   └────────────────────────────┴─────────────────────────────┘
                                │ Wrapped into IP packet
                                ▼
3. Network Layer (IP)
   ┌────────────────────────────┬─────────────────────────────┐
   │ Source IP: 127.0.0.1       │ Destination IP: 127.0.0.1   │ + [UDP Datagram]
   └────────────────────────────┴─────────────────────────────┘
                                │ Sent onto Network Interface (NIC / Loopback)
                                ▼
4. Destination (Port 5060)
   Remote OS delivers packet to the process listening on UDP 5060 (e.g., Linphone / PBX)





       PC A (Your App)                                  PC B (Callee / Softphone)
       IP: 192.168.1.10                                       IP: 192.168.1.20
       Port: 5070                                             Port: 5060
           │                                                      │
           │ 1. UDP Packet: INVITE                                │
           ├─────────────────────────────────────────────────────►│ (PC B receives packet)
           │                                                      │
           │ 2. UDP Packet: 100 Trying                            │
           │◄─────────────────────────────────────────────────────┤ (PC B says: "Got it, locating user")
           │                                                      │
           │ 3. UDP Packet: 180 Ringing                           │
           │◄─────────────────────────────────────────────────────┤ (Phone starts ringing! 🔔)
           │                                                      │
           │    [User on PC B clicks "Answer" / "Pick Up"]        │
           │                                                      │
           │ 4. UDP Packet: 200 OK                                │
           │◄─────────────────────────────────────────────────────┤ (PC B says: "Call answered!")
           │                                                      │
           │ 5. UDP Packet: ACK                                   │
           ├─────────────────────────────────────────────────────►│ (PC A confirms: 3-way handshake complete)
           │                                                      │
           │══════════════════════════════════════════════════════│
           │        Direct Audio Streams Flow (RTP / Voice)       │
           │══════════════════════════════════════════════════════│





Three way handshake:
1. Caller ─────────── INVITE (Offer) ──────────► Callee
2. Caller ◄────────── 200 OK (Answer) ────────── Callee
3. Caller ─────────── ACK (Confirm) ───────────► Callee