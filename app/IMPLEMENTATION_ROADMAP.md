# AudioTagger Implementation Roadmap

## Current Status
✅ **Project Setup Complete**
✅ **Basic NFC Reading Working**
✅ **Audio Recording Infrastructure**
✅ **Database Layer (Room)**
✅ **Foreground Service Architecture**

## Refactoring Plan

### Phase 1: Core App Structure Overhaul (3-4 hours)

#### 1.1 Simplify Main Screen
- **Remove:** Complex navigation logic, test buttons, create mode
- **Add:** Two large buttons: "Record Audio" and "My Tags"
- **Update:** Clean, minimal design with large touch targets

#### 1.2 Create Dedicated Recording Activity
- **New Activity:** `RecordingActivity` with streamlined flow
- **Features:** Visual timer, large stop button, countdown beeps
- **Flow:** Record → Stop → "Scan tag to save" → Success

#### 1.3 Create Tag Info Activity
- **New Activity:** `TagInfoActivity` for individual tag display
- **Features:** Large play button, tag details, edit/delete options
- **Purpose:** Foreground playback solution for NFC scans

#### 1.4 Update NFC Handling
- **Modify:** `NFCDispatchActivity` to launch `TagInfoActivity`
- **Remove:** Background audio service complexity
- **Simplify:** Direct tag info display with user-controlled playback

### Phase 2: Enhanced User Experience (2-3 hours)

#### 2.1 Settings Implementation
- **New Activity:** `SettingsActivity`
- **Features:** Audio prompts toggle, recording length limits
- **Storage:** SharedPreferences for user preferences

#### 2.2 Tag Management Screen
- **New Activity:** `TagListActivity`
- **Features:** View all tags, search, edit labels, delete
- **Design:** Large list items with clear actions

#### 2.3 Visual Polish
- **Update:** Material Design 3 components
- **Implement:** Consistent color scheme and spacing
- **Enhance:** Icons, animations, and visual feedback

### Phase 3: Advanced Features (3-4 hours)

#### 3.1 Text-to-Speech Option
- **Add:** "Add Text" button on main screen
- **Create:** Text input activity with TTS preview
- **Integrate:** TTS tags into existing infrastructure

#### 3.2 Import/Export System
- **Implement:** JSON-based tag export
- **Create:** Share functionality for tag collections
- **Add:** Import capability for received tag sets

## File Structure Changes

### New Files to Create
```
├── activities/
│   ├── RecordingActivity.kt
│   ├── TagInfoActivity.kt
│   ├── TagListActivity.kt
│   ├── SettingsActivity.kt
│   └── TextInputActivity.kt
├── models/
│   ├── UserPreferences.kt
│   └── TagExportModel.kt
├── utils/
│   ├── AudioUtils.kt
│   ├── TimeUtils.kt
│   └── ExportUtils.kt
└── layouts/
    ├── activity_recording.xml
    ├── activity_tag_info.xml
    ├── activity_tag_list.xml
    ├── activity_settings.xml
    └── item_tag_list.xml
```

### Files to Simplify/Refactor
- **MainActivity.kt** → Simplified navigation hub
- **MainScreen.kt** → Two-button design
- **NFCDispatchActivity.kt** → Launch TagInfoActivity
- **TagCreationViewModel.kt** → Split into specific ViewModels

### Files to Remove/Replace
- **TagCreationScreen.kt** → Replace with RecordingActivity
- **ManageTagsScreen.kt** → Replace with TagListActivity
- **NFCAudioService.kt** → Remove (foreground approach)
- **Complex ViewModels** → Simplify for specific use cases

## Technical Improvements

### Database Schema Updates
```kotlin
@Entity(tableName = "audio_tags")
data class AudioTag(
    @PrimaryKey val id: String,
    val label: String = "",
    val type: String, // "AUDIO" or "TTS"
    val content: String, // File path or text content
    val created: Long = System.currentTimeMillis(),
    val duration: Int? = null, // Duration in seconds for audio
    val lastPlayed: Long? = null
)
```

### Simplified Navigation
```kotlin
// Remove complex navigation graph
// Use explicit Intent-based navigation between activities
// Each activity has clear, single purpose
```

### Improved NFC Flow
```
NFC Scan → 
NFCDispatchActivity → 
TagInfoActivity(tagId) →
Large [PLAY] button →
Immediate audio playback
```

## Success Metrics

### Phase 1 Success
- [ ] App launches to clean main screen in <2 seconds
- [ ] Record button starts recording in <1 second
- [ ] NFC scan shows tag info screen with play button
- [ ] Audio playback works reliably in foreground

### Phase 2 Success
- [ ] Settings toggle for audio prompts works
- [ ] Tag list shows all saved tags with basic operations
- [ ] Visual design is clean and accessible
- [ ] All buttons meet 56dp minimum touch target

### Phase 3 Success
- [ ] Text-to-speech option functional
- [ ] Export/import system working
- [ ] App feels polished and professional
- [ ] User testing feedback is positive

## Risk Mitigation

### Technical Risks
- **Database Migration:** Keep existing schema compatible
- **NFC Compatibility:** Test with various tag types
- **Audio Format Issues:** Stick with proven AAC format

### UX Risks
- **Accessibility Regression:** Test with TalkBack throughout
- **Performance Impact:** Profile app launch and response times
- **User Confusion:** Keep flows simple and predictable

This roadmap provides a clear path from our current complex implementation to a clean, user-friendly app that solves the core accessibility problem elegantly.