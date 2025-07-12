# Automated APK Building with GitHub Actions

This repository uses GitHub Actions to automatically build APKs for releases and testing.

## 📋 **Available Workflows**

### 1. **Build APK** (`build-apk.yml`)
**Trigger**: Manual dispatch or git tags
- ✅ Builds debug or release APKs
- ✅ Automatically creates GitHub releases for tags
- ✅ Uploads APKs as artifacts
- ✅ Handles version naming

### 2. **Build Release** (`build-release.yml`) 
**Trigger**: GitHub release creation
- ✅ Builds both debug and release APKs
- ✅ Signs release APKs (requires secrets)
- ✅ Attaches APKs to GitHub releases
- ✅ Production-ready builds

### 3. **CI** (`ci.yml`)
**Trigger**: Push to main/develop, pull requests
- ✅ Runs unit tests
- ✅ Performs lint checks
- ✅ Builds debug APK for validation
- ✅ Uploads test reports

## 🚀 **How to Use**

### **Automatic Release Building**
1. **Create a git tag**:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. **APK automatically builds** and attaches to release

### **Manual APK Building**
1. Go to **Actions** tab in GitHub
2. Select **"Build APK"** workflow
3. Click **"Run workflow"**
4. Choose **debug** or **release** build
5. Download APK from **Artifacts**

### **Release Process**
1. **Create release** on GitHub
2. **Release workflow** automatically:
   - Builds signed APKs
   - Attaches to release
   - Ready for distribution

## 🔐 **App Signing Setup (Optional)**

For production releases, set up these GitHub secrets:

### **Required Secrets**
- `SIGNING_KEY`: Base64 encoded keystore file
- `ALIAS`: Keystore alias name
- `KEY_STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password

### **Generate Signing Key**
```bash
# Create keystore
keytool -genkey -v -keystore audiotag-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias audiotag

# Convert to base64 for GitHub secret
base64 audiotag-release-key.jks | pbcopy
```

### **Add Secrets to GitHub**
1. Repository **Settings** → **Secrets and variables** → **Actions**
2. Add **New repository secret**
3. Paste base64 keystore as `SIGNING_KEY`
4. Add other passwords/alias

## 📱 **Build Types**

### **Debug APK**
- ✅ **Unsigned** (install with "Unknown Sources")
- ✅ **Debugging enabled**
- ✅ **Fast builds**
- ✅ **Testing and development**

### **Release APK**
- ✅ **Signed** (production ready)
- ✅ **Optimized code**
- ✅ **Google Play compatible**
- ✅ **End user distribution**

## 🔄 **Workflow Triggers**

### **Automatic Triggers**
- **Push to main**: CI validation
- **Create tag**: Build and release APK
- **Create release**: Build signed APKs
- **Pull request**: CI validation

### **Manual Triggers**
- **Build APK**: On-demand APK building
- **Build Release**: Manual release building

## 📦 **Artifacts and Downloads**

### **From Workflow Artifacts**
1. **Actions** tab → **Workflow run**
2. **Artifacts** section
3. **Download** ZIP file with APKs

### **From GitHub Releases**
1. **Releases** tab
2. **Assets** section
3. **Direct APK download**

## 🛠️ **Customization**

### **Version Naming**
- **Tags**: Uses git tag (e.g., `v1.0.0`)
- **Manual**: Uses timestamp
- **Format**: `AudioTag-v1.0.0-debug.apk`

### **Build Configuration**
Edit workflows to customize:
- **JDK version** (currently 17)
- **Build tools version**
- **Additional build steps**
- **Test execution**

## 🔍 **Monitoring Builds**

### **Build Status**
- **Actions** tab shows all runs
- **Green checkmark**: Success ✅
- **Red X**: Failed ❌
- **Yellow circle**: In progress 🟡

### **Troubleshooting**
- **Click workflow run** for detailed logs
- **Check each step** for error messages
- **Common issues**: Signing, dependencies, permissions

## 📄 **Benefits**

### **Automation**
- ✅ **No manual building** required
- ✅ **Consistent builds** across environments
- ✅ **Automated testing** before releases

### **Distribution**
- ✅ **Easy download** from GitHub
- ✅ **Version tracking** with releases
- ✅ **Multiple build types** available

### **Quality**
- ✅ **CI validation** on every commit
- ✅ **Automated testing** prevents regressions
- ✅ **Lint checks** maintain code quality

---

**Your AudioTag releases are now fully automated! 🚀**