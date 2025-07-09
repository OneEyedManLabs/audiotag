# AudioTagger: Requirements & Implementation Plan (Version 2.0)

## 1. Vision

Create a simple, reliable, and highly accessible Android application that empowers visually impaired individuals to label their physical world with audio notes using NFC technology. The app prioritizes ease of use, visual accessibility, and seamless user experience over complex features.

## 2. Core User Stories

- **As a visually impaired user, I want to** quickly record audio for an object and associate it with an NFC tag in under 30 seconds
- **As a visually impaired user, I want to** scan any programmed NFC tag and immediately hear the associated audio without complex navigation
- **As a user, I want to** see clear, large buttons and have the option to disable audio prompts when I become familiar with the app
- **As a user, I want to** manage my tags with simple operations: play, label, delete, and organize

## 3. Revised App Flow

### Main Screen
```
┌─────────────────────────────┐
│        AudioTagger          │
│                             │
│    [🎤 Record Audio]        │
│                             │
│    [💬 Add Text]            │
│                             │
│    [📋 My Tags]             │
│                             │
│    [⚙️ Settings]            │
│                             │
│    [🔍 Scan Tag to Play]    │
└─────────────────────────────┘
```

### Recording Flow
```
Tap "Record Audio" → 
"beep, beep, beep" countdown → 
Recording with timer/progress → 
Large [STOP] button → 
"Scan tag to save" prompt →
Success confirmation
```

### NFC Scan Response (Foreground Solution)
```
Scan NFC Tag →
Launch to Tag Info Screen:

┌─────────────────────────────┐
│    🏷️ Kitchen Spice Rack    │
│                             │
│    Created: Today, 2:30 PM  │
│    Duration: 0:15           │
│                             │
│    [🔊 PLAY AUDIO]          │
│                             │
│    [✏️ Edit]  [🗑️ Delete]   │
└─────────────────────────────┘
```

## 4. Functional Requirements (Revised)

### Core Features (MVP)
- **FR1: Simple Recording:** One-tap audio recording with visual timer and large stop button
- **FR2: NFC Association:** Scan any NFC tag to associate with recorded audio
- **FR3: Foreground Playback:** Launch app to tag info screen with large play button (solves background audio issues)
- **FR4: Audio Prompts Toggle:** Settings option to disable TTS prompts for experienced users
- **FR5: Large UI Elements:** All buttons minimum 56dp touch targets for accessibility

### Secondary Features (Post-MVP)
- **FR6: Text-to-Speech:** Alternative to audio recording for text-based labels
- **FR7: Tag Management:** View, edit, delete, and organize existing tags
- **FR8: Audio Limits:** Configurable maximum recording length (30-60 seconds)
- **FR9: Visual Feedback:** Progress bars, clear recording states, haptic feedback

### Advanced Features (Future)
- **FR10: Export/Import:** Share tag collections between users
- **FR11: Search/Categories:** Organize tags by type or usage
- **FR12: Backup/Sync:** Cloud storage integration

## 5. Technical Architecture (Simplified)

### Background Audio Solution
Instead of fighting Android's background activity restrictions:
1. **NFC Scan** → Launch app to foreground
2. **Tag Info Screen** → Show tag details with large play button
3. **User Interaction** → Explicit play action satisfies Android requirements
4. **Immediate Playback** → No permission issues in foreground

### Data Structure
```kotlin
data class AudioTag(
    val id: String,              // NFC tag ID or generated ID
    val label: String,           // User-friendly name
    val type: TagType,           // AUDIO or TTS
    val content: String,         // File path or text content
    val created: Long,           // Timestamp
    val duration: Int? = null    // Audio duration in seconds
)
```

### Core Components
- **MainActivity:** Navigation hub with large buttons
- **RecordingActivity:** Streamlined recording experience
- **TagInfoActivity:** Display and play individual tags
- **TagListActivity:** Manage multiple tags
- **SettingsActivity:** Audio prompts toggle, recording limits

## 6. User Experience Priorities

### Accessibility First
- **Large Touch Targets:** 56dp minimum, 72dp preferred
- **High Contrast:** Clear visual hierarchy
- **TalkBack Support:** Full screen reader compatibility
- **Haptic Feedback:** Vibration for all major actions
- **Audio Cues:** Distinctive beeps and prompts

### Simplicity Focus
- **One-Touch Actions:** Record, stop, play with single taps
- **Clear States:** Obvious visual feedback for all operations
- **Error Prevention:** Confirm destructive actions
- **Consistent Layout:** Same button positions across screens

### Performance Goals
- **Fast Launch:** App ready in under 2 seconds
- **Instant Recording:** Start recording within 1 second of tap
- **Quick Playback:** Audio starts within 500ms of play button
- **Responsive UI:** No lag in button responses

## 7. Implementation Phases

### Phase 1: Core Recording & Playback (Week 1-2)
1. Simple main screen with two buttons: Record Audio, My Tags
2. Streamlined recording flow with visual timer
3. NFC tag association after recording
4. Tag info screen with large play button
5. Basic tag storage and retrieval

**Success Criteria:** User can record 30-second audio, associate with NFC tag, and play it back reliably

### Phase 2: Management & Polish (Week 3)
1. Tag list screen with basic operations
2. Settings screen with audio prompts toggle
3. Edit tag labels and delete functionality
4. Improved visual design and accessibility
5. Comprehensive error handling

**Success Criteria:** User can manage multiple tags and customize experience

### Phase 3: Advanced Features (Week 4+)
1. Text-to-speech option for non-audio tags
2. Export/import functionality for tag sharing
3. Search and categorization features
4. Performance optimizations
5. User testing and feedback integration

## 8. Design Guidelines

### Visual Hierarchy
- **Primary Actions:** Large, colorful buttons (Record, Play)
- **Secondary Actions:** Medium buttons (Edit, Delete)
- **Navigation:** Standard size but clear icons
- **Text:** Large, high-contrast, scalable fonts

### Color Scheme
- **Primary:** High-contrast blue for main actions
- **Success:** Green for confirmations and completed states
- **Warning:** Orange for recording states and warnings
- **Error:** Red for destructive actions and errors
- **Background:** Clean whites/grays for easy reading

### Interaction Patterns
- **Tap:** Primary action (play, record, stop)
- **Long Press:** Secondary action (edit, options)
- **Swipe:** Navigation between screens
- **Haptic:** Confirm all major state changes

This revised approach prioritizes user experience and accessibility while solving the technical challenges we encountered. The foreground-launch solution for NFC scanning eliminates Android restrictions while actually improving usability by giving users explicit control over playback.