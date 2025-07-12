# AudioTag

**Turn any NFC tag into a personal audio note**

AudioTag is an Android app that lets you record audio messages and associate them with NFC tags. Simply scan an NFC tag to instantly play back your recorded message - perfect for voice memos, instructions, reminders, or creative audio experiences.

## Features

### 🎤 Audio Recording
- **High-quality audio recording** with customizable settings
- **Visual countdown** with audio cues for recording start
- **Re-record functionality** to update existing tags
- **Accessible design** with TalkBack support

### 💬 Text-to-Speech Tags
- **Create text tags** that speak your message using TTS
- **Preview functionality** to hear how text will sound
- **Supports multiple languages** through Android TTS

### 🏷️ Tag Management
- **Organize tags with groups** (Work, Personal, Music, etc.)
- **Add titles and descriptions** for easy identification
- **Edit tag metadata** without re-recording
- **Visual tag list** with search and filtering

### 📱 NFC Integration
- **Instant playback** when scanning known tags
- **Unknown tag detection** with options to create new associations
- **Background NFC processing** works even when app is closed
- **Supports various NFC tag types** (NTAG213, NTAG215, NTAG216, etc.)

### 🎨 Accessibility & Theming
- **Full TalkBack support** for visually impaired users
- **Multiple themes**: Light, Dark, System, High Contrast
- **Large touch targets** and clear visual feedback
- **Customizable TTS settings** with enable/disable toggle

### 💾 Backup & Export
- **Android Auto Backup** for seamless device transfers
- **Manual export/import** system for sharing tags
- **Group-based export** for organized backups
- **Smart file management** with size-based categorization

## Getting Started

### Requirements
- Android 7.0 (API level 24) or higher
- NFC-enabled device
- NFC tags (NTAG213, NTAG215, NTAG216 recommended)

### Installation
1. Download the APK from the [Releases](https://github.com/oneeyedmanlabs/audiotag/releases) page
2. Enable "Install from Unknown Sources" in your device settings
3. Install the APK file
4. Grant necessary permissions (NFC, Audio Recording, Vibration)

### First Use
1. **Enable NFC** on your device (Settings > Connected devices > NFC)
2. **Launch AudioTag** and grant permissions
3. **Choose your creation method**:
   - **Record Audio**: Record a voice message
   - **Create Text Tag**: Type text for TTS playback
4. **Scan an NFC tag** when prompted to associate your recording
5. **Test playback** by scanning the tag again

## Usage Guide

### Recording Audio Tags
1. Tap **"Record Audio"** on the main screen
2. Enter a **title** and optional **description**
3. Select **groups** for organization
4. Tap **"Start Recording"** and speak your message
5. **Preview** your recording and re-record if needed
6. **Scan an NFC tag** to save the association

### Creating Text Tags
1. Tap **"Create Text Tag"** on the main screen
2. Type your **message content**
3. Use **"Preview"** to hear how it will sound
4. Add **title, description, and groups**
5. **Scan an NFC tag** to create the association

### Managing Tags
- **View all tags**: Tap "My Tags" to see your collection
- **Edit tags**: Long-press any tag to edit details
- **Filter by group**: Use the group filter in the tag list
- **Export tags**: Use the export feature for backups

### Scanning Tags
- **Automatic playback**: Scan known tags for instant audio
- **Unknown tags**: Choose to record audio or create text
- **Background scanning**: Works even when app is closed

## Settings

### TTS (Text-to-Speech)
- **Enable/disable TTS**: Toggle voice guidance and text tag playback
- **Language settings**: Configure through Android TTS settings

### Themes
- **Light**: Clean, bright interface
- **Dark**: Easy on the eyes for low-light use
- **System**: Follows device theme settings
- **High Contrast**: Enhanced visibility for accessibility

### Backup & Export
- **Auto Backup**: Enabled by default for seamless sync
- **Export Options**: Choose between all tags or specific groups
- **Import**: Restore tags from exported files

## Technical Details

### Supported NFC Tags
- **NTAG213** (144 bytes) - Basic use
- **NTAG215** (504 bytes) - Recommended
- **NTAG216** (924 bytes) - Large capacity
- **Generic ISO14443 Type A** tags

### Audio Specifications
- **Format**: AAC/MP3 encoding
- **Sample Rate**: 44.1 kHz
- **Bit Rate**: 128 kbps
- **Maximum Duration**: 10 minutes per recording

### Storage
- **Local storage**: All data stored on device
- **Backup integration**: Compatible with Android Auto Backup
- **Export format**: Custom JSON format with metadata

## Troubleshooting

### NFC Issues
- **Tag not detected**: Ensure NFC is enabled and tag is compatible
- **Inconsistent scanning**: Try different tag positions and hold steady
- **"Empty tag" error**: Use unknown tag flow to reassociate

### Audio Issues
- **No playback**: Check volume settings and grant audio permissions
- **Poor quality**: Ensure quiet environment during recording
- **TTS not working**: Verify Android TTS settings and language packs

### App Issues
- **Crashes**: Clear app cache or reinstall
- **Slow performance**: Free up device storage
- **Backup not working**: Check Google account sync settings

## Privacy & Security

AudioTag prioritizes your privacy:
- **Local storage only**: All recordings stay on your device
- **No cloud uploads**: Audio never leaves your device without your consent
- **Minimal permissions**: Only requests necessary access
- **Open source**: Full transparency with GPL-3.0 license

## Contributing

AudioTag is open source software released under the GPL-3.0 license. Contributions are welcome!

### Development Setup
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Run on device or emulator

### Reporting Issues
- Use the [GitHub Issues](https://github.com/oneeyedmanlabs/audiotag/issues) page
- Include device model, Android version, and steps to reproduce
- Attach logs if possible

### Feature Requests
- Check existing issues before creating new ones
- Describe the use case and expected behavior
- Consider accessibility implications

## License

AudioTag is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for details.

This means you can:
- ✅ Use the app for personal or commercial purposes
- ✅ Study and modify the source code
- ✅ Share the app with others
- ✅ Distribute modified versions

**Requirements:**
- 📋 Keep the same license for modified versions
- 📋 Share source code when distributing
- 📋 Document any changes made

## Support

- **Documentation**: Check this README and in-app help
- **Issues**: [GitHub Issues](https://github.com/oneeyedmanlabs/audiotag/issues)
- **Email**: [support@oneeyedmanlabs.org](mailto:support@oneeyedmanlabs.org)

## Acknowledgments

- Built with **Android Jetpack Compose** for modern UI
- Uses **Room Database** for local storage
- **Material Design 3** for consistent design language
- **Android TTS** for text-to-speech functionality

---

**Made with ❤️ for the NFC community**