# Intercom-Alpha · Transport and product comparison

27 November 2026 · Source audit of Sprint 37 closeout 0201dd0 and the Sprint 38 planning worktree

Scope covers the rider path across native entry points, shared audio and mesh logic, platform transports, signaling and server, test and sprint evidence. This is a source and documentation audit. Physical phones, signed-in storefront installations and ride performance were outside the evidence available.

## Which radios and transports we have

| Path | Current source and evidence | Recommendation |
|---|---|---|
| Bluetooth audio accessory | A phone routes speech through platform audio and compatible headsets. This is separate from the phone-to-phone link. | Validate call-route microphone selection, interruption and reconnect with ordinary earbuds and helmet headsets. |
| BLE GATT phone-to-phone | Android and iOS implementations advertise, discover, connect, exchange audio/control packets and deduplicate incoming packets. This is the existing mixed-platform path in source. The reviewed loops gate voice transmission on PTT-related state. | Establish hands-free two-way voice, then measure reliable two- and four-rider operation with phones locked. |
| Multi-hop over BLE | The native transports forward relay envelopes with a TTL. Repeater and directed routing code can construct those envelopes. Ordinary native sendAudio emits a plain Audio packet, and its receive branch delivers plain Audio locally. | Prove live A→B→C relaying with A and C unable to exchange directly and with B as an ordinary rider. Record separately what needs repeater mode. |
| Apple Multipeer Connectivity | iOS initializes an MCSession and attempts sends on that path alongside BLE when peers are present. This gives an Apple-only path and is not mixed-platform Wi-Fi. | Measure whether it improves iPhone sessions and verify background behavior before depending on it. |
| Local Wi-Fi or hotspot LAN | No dedicated mixed-platform LAN voice adapter was identified. A shared local IP network is the closest analogue to NearVox. | Prototype Android host and iPhone host separately with mobile data disabled and test join, host loss and lock-screen recovery. |
| Wi-Fi Direct | Listed in the older project spec. No platform adapter was identified. Android has a public Wi-Fi Direct API. Apple documents Apple peer-to-peer Wi-Fi and Wi-Fi Aware as its public peer-to-peer options. | Evaluate as an Android-specific candidate or test an Android group owner with an iPhone as an ordinary Wi-Fi client. |
| Wi-Fi Aware | No adapter identified. Apple provides it on iOS 26 with iPhone 12 or later and requires an entitlement. Android support varies by device. | Prototype cross-platform discovery and sustained speech on supported pairs before making compatibility claims. |
| Google Nearby Connections | Mentioned in the older spec as an Android upgrade. No active SDK adapter identified. Google now documents an Android–iOS SDK. | Compare setup, permissions, dependency coverage, lock-screen behavior and live audio against shared LAN. |
| Internet fallback | Ktor signaling/server and shared cloud relay and hybrid routing components exist. I found no mobile construction of HybridTransportRouter or CloudRelayBridge in the reviewed source. | Preserve the components and prove actual handoff separately from the offline rider gate. |
| Adaptive BLE PHY | Sprint 37 added policy, telemetry and fleet simulation. The closeout leaves physical range and applied PHY open. | Capture the negotiated PHY and usable audio range on actual phones before shipping a longer-range claim. |

[Android transport](../../app/sharedLogic/src/androidMain/kotlin/org/nowni/intercom_alpha/mesh/MeshTransport.android.kt) · [iOS transport](../../app/sharedLogic/src/iosMain/kotlin/org/nowni/intercom_alpha/mesh/MeshTransport.ios.kt) · [Earlier project spec](../../app/androidApp/PROJECT-SPEC.md) · [Sprint 37 closeout](2026-11-23-Sprint-37-Closeout-Report.md) · [Apple Wi-Fi APIs](https://developer.apple.com/documentation/technotes/tn3111-ios-wifi-api-overview) · [Android Wi-Fi Direct](https://developer.android.com/develop/connectivity/wifi/wifip2p) · [Android Wi-Fi Aware](https://developer.android.com/develop/connectivity/wifi/wifi-aware) · [Google Nearby Connections](https://developers.google.com/nearby/?hl=en)

## Engineering work worth carrying forward

| Area | Existing work | What earns a better rider outcome |
|---|---|---|
| Join and native UI | Android Compose and native SwiftUI, QR group onboarding, peer views, audio and headset controls. | Put Start and Join up front and separate real peer state, audio route, live microphone and recovery. |
| Audio | Platform capture/playback, ADPCM voice path, adaptive audio profiles, jitter, packet loss concealment and forward error correction components. | Trace which components the active transport uses, then measure speech overlap, latency, first syllables and wind intelligibility. |
| Mesh and airtime | Peripheral and central links, relay envelopes, deduplication, stationary repeater, directed routing, QoS, packet packing/interleaving and fleet congestion components or tests. | Prove relay without direct reach, then compare loss and recovery at useful rider spacing. The current platform transport broadcasts serialized packets directly even where it also enqueues them into the scheduler. |
| Privacy | Shared PacketCipher, group key and ratchet components. The reviewed native transport serializes MeshPacket directly. | Trace the authenticated/encrypted envelope end to end on every enabled path before claiming private on-air voice. |
| Convoy and operations | Radar, convoy telemetry, elevation/trail tooling, incident features, phone repeater, and a Sprint 38 Basecamp console plan. | Keep the primary ride simple. Verify accessory and operations flows separately. |

The best candidate architecture keeps one rider session and shared packet contract above platform adapters. Establish BLE as the compatibility baseline for the tested phones. Compare a local Wi-Fi voice path next. Use measured capability to choose transports and keep multi-hop forwarding a separate property. No transport superiority or automatic handoff is established by this audit.

## App comparison

Competitor rows describe reviewed vendor or storefront claims. Public listings establish visibility. Installation eligibility, real mixed-device behavior and range require physical verification.

| App | Intercom experience | Offline phone-to-phone transport | Platforms and India evidence | Practical difference |
|---|---|---|---|---|
| **Intercom-Alpha** | Continuous private rider conversation is the goal. Current primary controls and microphone sends are PTT-oriented. | Android/iOS BLE GATT. Apple-only Multipeer. Relay and repeater code exists. Mixed-platform Wi-Fi and live automatic handoff are unverified. | Android and iOS source. Public India rollout unverified. | Strongest opportunity is combining easy setup and natural speech with independently verified relaying and recovery. |
| **NearVox** | Advertised simultaneous hands-free speech, QR join, reconnect, spoken alerts and Pro host handover. | Shared phone-hosted Wi-Fi. Vendor describes up to eight participants, with host/device limits. | Android/iOS. Original app's India install unverified. | Benchmark pre-ride setup and screen-off recovery. [NearVox](https://nearvox.de/en/) |
| **MeshIt** | Always-transmit and VOX modes, relaying and screen-off use advertised. | Nearby radios, with shared Wi-Fi/hotspot documented for mixed groups. | Android/iOS. India iOS listing visible. | Closest direct alternative for mixed offline riding. [India App Store](https://apps.apple.com/in/app/meshit-riding-intercom/id6834731202) |
| **Sena WAVE** | Live Geo Wave and private Friends Wave. | Cellular voice with compatible Sena hardware Mesh backup. | Android/iOS listings and Sena India page visible. | Private group and fallback reference. [Sena](https://www.sena.com/wave-intercom/) |
| **Infinite iNtercom** | Local group voice and PTT-oriented controls. | Android Bluetooth/Wi-Fi and iOS Wi-Fi documented. Mixed pairing needs verification. | India iOS and India-localized Play listings visible. | Local group-call and setup comparator. [India App Store](https://apps.apple.com/in/app/infinite-intercom/id1206022901) |
| **Offline Intercom** | VOX/PTT, room code, recent devices and screen-off group calling advertised. | Wi-Fi Direct. | Android 12+ listing. India install unverified. | Android Wi-Fi Direct reference. [Google Play](https://play.google.com/store/apps/details?id=com.motointercom) |
| **BlinkTalk** | Simultaneous conversation for up to 17, headset support and auto-reconnect advertised. | Internet-based. | Android listing visible, which describes iPhone support as forthcoming. | Reference for channel setup and disconnected-rider status. [Google Play](https://play.google.com/store/apps/details?id=com.blinkvisa.blinkchat) |
| **Vocari** | Nearby voice candidate from the original search. | Current implementation details unverified. | Historical Android listing link. Current India installation unverified. | Include in hands-on comparison if available. [Original listing link](https://play.google.com/store/apps/details?id=com.vocari.vocari2) |
| **Cardo PACKTALK EDGE + Connect** | Live group conversation and voice controls. | Dedicated helmet DMC hardware. | Android/iOS companion app. India retailer listing reviewed. | Hardware benchmark for rider audio and self-healing groups. [Cardo](https://cardosystems.com/products/packtalk-edge) |
| **Zello** | Internet PTT channels and supported buttons. | Internet required in consumer app. | Android/iOS, public India iOS listing. | Secondary PTT reference. [Zello](https://support.zello.com/zc/connection-requirements) |
| **CRUIZR** | India-focused ride coordination, voice and convoy tools advertised. | Offline mesh is marketed, while the Play listing describes it as in development. | Android listing, iOS described as forthcoming. | Compare local onboarding and positioning. [Google Play](https://play.google.com/store/apps/details?id=com.cruizr.app) |
| **Bridgefy** | Offline text and voice notes. | Bluetooth message relay after first activation. | Android/iOS listings. India install unverified. | Reference for message relay. [Bridgefy](https://bridgefy.me/app/) |

[Market research](2026-11-27-Intercom-Market-Research.md) · [Product audit and council](2026-11-27-Rider-Intercom-Product-Audit.md)

## Hybrid topology decision and verification sequence

The owner selected a hybrid topology for one continuous rider conversation. Use direct links for nearby pairs, a partial mesh with rider relays when the group stretches, and a shared Wi-Fi segment where device testing supports it. Use alternative routes when available. When the graph partitions, report the loss of group reachability and recover membership and audio when a path returns. Preserve intentional mute throughout recovery.

Start with BLE GATT. Test Android advertising and iPhone connecting for mixed links, then measure lock-screen rediscovery. Evaluate shared local Wi-Fi after the BLE baseline. Allow a phone to bridge transports only after testing simultaneous BLE and Wi-Fi operation on that phone. Keep internet relay optional and verify it separately.

| Gate | Setup | Evidence required |
|---|---|---|
| Two phones | Android–Android, iPhone–iPhone, and one of each. | Offline simultaneous voice, mute, audio routing, locked-screen continuity, and recovery from a forced link loss. Record roles and permissions. |
| Three phones | Android–iPhone–Android and iPhone–Android–iPhone. Prevent direct endpoint traffic. | Both endpoints hear each other through the middle phone without PTT. A trace identifies the relay. Removing the middle device breaks the path. |
| Alternate path | Four phones with two independent relay paths. | Remove the active relay and measure audio interruption and recovery. Check for duplicate playback. Remove both paths and verify split status and automatic rejoin. |
| Ten riders | Four iPhones and six Android phones, first interleaved, then grouped by operating system. Repeat with each platform providing the only cross-group bridge. | Mixed-group voice, overlapping speakers, measurable recovery and correct partition status. Check microphone mute after reconnect. |
| Twenty riders | Representative mixed convoy and a deliberately stretched route. | Record time to first voice, delay by hop count, intelligibility, reconnection time, and battery drain. Preserve device, OS, headset, topology, build and route details. |

The immediate code investigation is the ordinary rider send path. Confirm how a joined and unmuted session authorizes transmission, where ordinary Audio becomes forwardable, and where group validation and duplicate suppression happen. Review those boundaries before increasing the hop budget or adding a second transport. Physical tests determine the supported rider count, spacing and hop budget.

This sequence is proposed work for the product council. Sprint 38 Basecamp sequencing remains a separate owner decision.

## Creator departure and real-ride recovery

Proposed rider behavior. Current code has GroupManager local leave, local leadership mutation and an isolated MeshElectionManager with tests. The source review found no production instantiation of that election manager. Android's shared Home LeaveGroup action clears view state and the Android notification has a separate service stop action. The iOS bridge calls the group manager's local leave. Verify membership, transport and microphone teardown across native entry points. Distributed creator handoff and authorization are open.

Keep three roles separate. Creator starts the ride. An optional delegated admin can approve late joins. A relay or hotspot host carries traffic. An established rider should be able to speak without the creator present when a usable path and valid membership remain. Allow multiple admins. Existing admins keep their role when one leaves. When the last admin intentionally leaves, promote the earliest-joined eligible remaining member using the agreed membership order. If the handoff cannot be confirmed, keep existing riders talking and pause new admissions. During a partition, pause changes that would conflict across subsets until authorized state can be reconciled. Distinguish leaving intentionally from temporary loss. Revoke the departed member's live access and rotate credentials as required by the group protocol. On return, an intentional leaver needs a fresh authorized join, while a temporarily disconnected rider can resume if still authorized. Avoid stale invites and duplicate membership.

[Group manager](../../app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/group/GroupManagerImpl.kt) · [Election component](../../app/sharedLogic/src/commonMain/kotlin/org/nowni/intercom_alpha/group/MeshElectionManager.kt)

| Real ride event | Expected behavior | Reproducible field check |
|---|---|---|
| Creator takes an early exit | Leave group removes that rider and stops local capture and transmission. Others continue where a route remains. A remaining admin or confirmed successor handles new joins. | With a mixed group, have the creator leave while two other riders talk. Verify continued audio, membership removal and old invite behavior. |
| Creator battery dies or app is terminated | Mark unreachable separately from intentional leave. Existing riders remain in the group. Resume only if still authorized. | Power off and restart the creator phone. Repeat with the phone also acting as the only relay. |
| Hotspot host leaves at a fuel stop | Use another already validated route if available. Otherwise indicate interruption and recover via a replacement hotspot only where the OS and join permissions allow it. | Disable the active hotspot with screens locked. Repeat with Android and iPhone as host. Measure prompts, time to voice and manual actions. |
| A truck, bend or traffic light splits the ride | Continue voice inside each connected subset. Report unreachable riders once, then restore the roster after reconnection. | Reproduce safely using separation and obstructions. Keep some phones connected on each side. Verify speech and admin controls before and after reunification. |
| A relay rider stops to refuel | Switch to a viable alternate path. If none exists, indicate the split. | Run the same loss once with a redundant link and once without. Check duplicate playback and recovery time. |
| A late rider arrives after departure | Require a valid invitation or authorized approval. Existing audio continues during an attempted join. | Test a late join with co-admin present, then absent. Repeat with expired or revoked invites. |
| Headset battery dies or route switches | Pause private audio on an unexpected route loss, preserve intentional mute and show or announce the issue where possible. Avoid automatically playing the group through the phone speaker. | Disconnect a headset, pair it again, and switch between two paired routes. Verify mic, output and mute. |
| A phone call, navigation prompt or music interrupts | Handle platform audio interruption and route changes, recover the session when permitted, and preserve prior mute. | Start navigation and media, take and decline calls, and check both phones locked. |
| Tunnel or intermittent mobile coverage | Continue local voice if the local mesh stays intact. Only rely on internet fallback where both sides have a working route. | Toggle mobile data and recreate a coverage gap. Record local continuity independently of any cloud fallback. |
| Wind, engines and several people talking | Keep speech intelligible and avoid clipping first syllables or repeatedly triggering VOX if enabled. | Compare stationary and controlled ride recordings. Measure overlap, lost words, delay and noise. |
| Battery saver, thermal pressure or OS process death | Surface degraded reachability and recover where OS permits. Preserve authorized session and explicit mute or require an intentional resume. | Repeat lock-screen and reconnection tests on representative devices with battery saver and after app restart. |
| Permission removed or local radio turned off | Surface the specific missing capability. Avoid showing a connected state without usable audio. | Disable Bluetooth, local network permission and microphone permission separately. |
| Nearby group uses the same name | Keep membership and audio isolated by authenticated session identity. | Create two nearby groups with identical names. Attempt stale and unauthorized QR joins. |

Start with controlled stationary checks, then use a planned route with bends, an open segment, a stop and staggered rider spacing. Riders operate phones only while stopped. Use an observer or device logs to capture event time, last intelligible speech, recovery time, affected riders, unexpected mic activity and any manual action. Benchmark published NearVox host handover where installable and treat its claims separately from observed results. [NearVox product FAQ](https://nearvox.de/en/) · [Apple audio route changes](https://developer.apple.com/documentation/avfaudio/responding-to-audio-route-changes) · [Android audio focus](https://developer.android.com/media/optimize/audio-focus)

### First implementation milestone · Admin departure

Use the WhatsApp-inspired continuity rule. The group survives the admin leaving. Allow admins to promote other members. When the last admin intentionally leaves, promote the earliest-joined eligible remaining member. Use the agreed admission order across devices. A member who explicitly leaves and rejoins gets a new position. Temporary disconnection preserves the original position and does not trigger succession. Keep established voice available when the last admin is unreachable. Pause admissions when nobody can prove authority. Restoring a radio route and transferring admin authority are independent operations.

| Order | Work | Acceptance evidence |
|---|---|---|
| 1 | Wire every native Leave action to shared session teardown. Stop the departing rider's capture, playback and participation, and update the remaining roster when reachable. | Android UI, Android notification and iOS Leave all stop local transmission. Other riders with a working path keep talking. |
| 2 | Prove hands-free voice through a mixed-platform relay. | Three phones with no direct endpoint link pass two-way audio and lock-screen checks, with each platform as the middle phone in a separate run. |
| 3 | Add and verify authorized admin handoff. Preserve current admins and wait for acknowledgement of an explicit transfer before showing it as complete. | A co-admin keeps authority. The earliest-joined eligible remaining member becomes admin after confirmed handoff and can approve a new join. An interrupted handoff never results in two conflicting successors. |
| 4 | Verify partitions and return. Treat temporary link loss separately from Leave. Resume voice for still-authorized riders and reconcile membership before accepting changes that could conflict. | Simulate departure, battery loss, stale invites and split/merge. Repeat on mixed phones. An intentional leaver needs a fresh authorized join and returns without admin rights. |

The 4-iPhone/6-Android field run follows these gates. Repeat creator loss with the creator as an ordinary peer, the sole relay, and the hotspot host. Record audible interruption, recovery time and any manual action. The council should review the evidence before proposing wider release or roadmap changes.

### Admin succession acceptance criteria

The agreed admission order is recorded with group membership. The first member admitted after the creator has first claim whenever they are still a member and no admin remains. Use the committed order across devices. A phone's clock and the order in which it receives messages cannot decide succession.

An authorized promotion adds an admin without demoting the person making the change. A confirmed intentional departure removes membership and its admin role together. If that would leave zero admins and at least one eligible member, the same confirmed change names the successor. Retrying that departure must produce the same result. If authority cannot be reconciled during a split, pause admissions and promotions while established riders continue talking.

| Case | Pass condition |
|---|---|
| A creates the group. B, C and D join in order. A leaves. | B becomes admin. C and D see the same result. B can approve a new join. |
| B then leaves as sole admin. | C becomes admin. |
| A promotes C and leaves. | C remains admin. B remains a member. |
| B leaves and later rejoins before A exits. | C succeeds A because B's new admission follows C and D. |
| The last admin loses signal, powers off, or returns. | Their role remains unchanged. No promotion is triggered by timeout. |
| A's departure update reaches Android twice and iPhone out of order. | Both converge on one successor. The repeated update does not promote another member. |
| A leaves during a split and succession cannot be confirmed across the group. | Unconfirmed partitions grant no new admin authority. Voice continues wherever a path exists. Reconnecting devices reconcile the committed handoff before allowing new admissions. |
| The sole remaining member leaves. | The session ends locally with no successor. |

Implementation starts by routing Android UI and notification Leave and iOS Leave through verifiable session teardown. Shared Kotlin owns membership and succession rules. Start with the cases above in shared tests and verify through both native clients. Keep role succession independent of relay selection and hotspot recovery.
