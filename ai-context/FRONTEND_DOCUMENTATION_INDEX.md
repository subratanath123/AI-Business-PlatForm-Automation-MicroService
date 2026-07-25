# Frontend Documentation Index

Complete guide for updating frontend code to work with the new SocialAsset collection.

---

## ⚠️ IMPORTANT CORRECTION

**Schedule endpoint updated:** `/v1/api/social-posts/schedule` (NOT `/v1/api/social-media/schedule`)

See: [FRONTEND_ENDPOINT_CORRECTION.md](FRONTEND_ENDPOINT_CORRECTION.md)

---

## 📚 Documents by Use Case

### For Busy Developers ⚡
Start here if you want the quickest overview:

**[FRONTEND_CHANGES_QUICK_GUIDE.md](FRONTEND_CHANGES_QUICK_GUIDE.md)** ✅ Updated
- TL;DR format
- 2-minute read
- Essential changes only
- Testing checklist
- Common questions answered

### For Implementation 💻
Full code examples for your project:

**[FRONTEND_CODE_MIGRATION_EXAMPLES.md](FRONTEND_CODE_MIGRATION_EXAMPLES.md)**
- 8 complete before/after examples
- React components
- Custom hooks
- Type definitions
- Error handling
- Draft storage

### For Complete Understanding 📖
Detailed reference with all context:

**[SOCIAL_ASSET_FRONTEND_INTEGRATION.md](SOCIAL_ASSET_FRONTEND_INTEGRATION.md)**
- Full integration guide
- API endpoint details
- Request/response examples
- Data flow diagrams
- Migration checklist
- React hook example
- Testing recommendations

### For Backend Context 🔧
Understanding what changed on the backend:

**[COLLECTION_SEPARATION_ARCHITECTURE.md](COLLECTION_SEPARATION_ARCHITECTURE.md)**
- Overall architecture
- Why changes were made
- Data flow comparison
- Collection breakdown
- Ownership models

---

## 🎯 Quick Start

### I just want to know what changed
→ Read: [FRONTEND_CHANGES_QUICK_GUIDE.md](FRONTEND_CHANGES_QUICK_GUIDE.md) (5 min)

### I need to update my code
→ Use: [FRONTEND_CODE_MIGRATION_EXAMPLES.md](FRONTEND_CODE_MIGRATION_EXAMPLES.md) (20 min)

### I want complete details
→ Study: [SOCIAL_ASSET_FRONTEND_INTEGRATION.md](SOCIAL_ASSET_FRONTEND_INTEGRATION.md) (30 min)

### I need to understand the backend
→ Check: [COLLECTION_SEPARATION_ARCHITECTURE.md](COLLECTION_SEPARATION_ARCHITECTURE.md) (15 min)

---

## 🔑 Key Changes at a Glance

**What Changed:**
- Upload response `mediaId` is now SocialAsset ID (was Attachment ID)

**What Stayed the Same:**
- Upload endpoint URL
- Response structure
- Schedule endpoint
- Media URL format
- Error codes

**What Improved:**
- File size limit: 50MB (was 12MB)
- Code clarity: Consistent field naming
- Type safety: Proper TypeScript types
- Maintainability: No field remapping needed

---

## 📋 Migration Checklist

### Phase 1: Understanding (15 min)
- [ ] Read FRONTEND_CHANGES_QUICK_GUIDE.md
- [ ] Understand that mediaId is now SocialAsset ID
- [ ] Note that everything else works the same

### Phase 2: Code Review (30 min)
- [ ] Review your upload handler
- [ ] Check your type definitions
- [ ] Look at draft storage code
- [ ] Check schedule post function
- [ ] Review error messages (50MB limit)

### Phase 3: Updates (1-2 hours)
- [ ] Update type definitions (optional but recommended)
- [ ] Update variable names (attachmentId → mediaId)
- [ ] Update field names (url → mediaUrl for consistency)
- [ ] Update error messages (12MB → 50MB)
- [ ] Test upload flow

### Phase 4: Testing (30 min)
- [ ] Unit tests for upload handler
- [ ] Integration tests for full flow
- [ ] Manual testing with different file sizes
- [ ] Test draft save/load
- [ ] Test schedule post with media

---

## 📊 Impact Summary

| Area | Impact | Effort |
|------|--------|--------|
| Type definitions | Recommended update | 10 min |
| Upload handler | No changes needed | 0 min |
| Variable names | Optional cleanup | 15 min |
| Schedule function | No changes needed | 0 min |
| Storage/draft | Optional improvement | 15 min |
| Error messages | Update limit (12MB→50MB) | 5 min |
| **Total** | **Low impact** | **~45 min** |

---

## 🚀 Implementation Steps

### Step 1: Update Types (Recommended)
```typescript
// Change
interface Media {
  attachmentId: string;
  url: string;
}

// To
interface UploadedMedia {
  mediaId: string;
  mediaUrl: string;
  mimeType: string;
  fileName: string;
  sizeBytes: number;
}
```

### Step 2: Update Upload Handler (Minimal)
```typescript
// Just use items directly - no transformation needed
setMedia(data.items);  // ✅ mediaId is already correct
```

### Step 3: Update Schedule Post (No Changes)
```typescript
// Already works - mediaId in response is SocialAsset ID
await schedulePost(content, media, platforms);
```

### Step 4: Update File Size Messages
```typescript
// Change: "Max 12MB" → "Max 50MB"
const MAX_FILE_SIZE = 50 * 1024 * 1024;
```

---

## ✅ Verification Checklist

After updating your code:

- [ ] Upload single file → mediaId received
- [ ] Upload multiple files → multiple mediaIds
- [ ] Remove media → works with mediaId
- [ ] Display media → all files show correctly
- [ ] Save draft → draft includes media
- [ ] Load draft → media loads correctly
- [ ] Schedule post → post created successfully
- [ ] 50MB file → uploads without error
- [ ] 51MB file → shows "exceeds 50MB" error
- [ ] No auth → shows "Authentication required" error

---

## 🔗 Related Documentation

**Backend:**
- [COLLECTION_SEPARATION_CHANGES.md](COLLECTION_SEPARATION_CHANGES.md)
- [SOCIAL_ASSET_SEPARATION_SUMMARY.md](SOCIAL_ASSET_SEPARATION_SUMMARY.md)
- [COLLECTION_SEPARATION_IMPLEMENTATION.md](COLLECTION_SEPARATION_IMPLEMENTATION.md)

**API Reference:**
- POST /v1/api/social-media/upload
- POST /v1/api/social-media/schedule

---

## ❓ Common Questions

**Q: Is this a breaking change?**
A: No. Your code works as-is. mediaId is used the same way.

**Q: Do I have to update my code?**
A: No, but it's recommended for consistency.

**Q: What if I don't change anything?**
A: Upload will work, but you'll get SocialAsset IDs instead of Attachment IDs.

**Q: Will old code break?**
A: No. The API is 100% backward compatible.

**Q: Can I keep using attachmentId?**
A: No, but you can rename the variable and use mediaId.

**Q: Is the media URL different?**
A: No. mediaUrl works exactly the same.

**Q: Can I upload 50MB files?**
A: Yes! That's new. Before it was 12MB.

**Q: What changed in the API?**
A: Only the source of the mediaId. Everything else is the same.

---

## 📞 Need Help?

1. **Quick question?** Check [FRONTEND_CHANGES_QUICK_GUIDE.md](FRONTEND_CHANGES_QUICK_GUIDE.md)
2. **Need code examples?** See [FRONTEND_CODE_MIGRATION_EXAMPLES.md](FRONTEND_CODE_MIGRATION_EXAMPLES.md)
3. **Want all details?** Read [SOCIAL_ASSET_FRONTEND_INTEGRATION.md](SOCIAL_ASSET_FRONTEND_INTEGRATION.md)
4. **Confused about backend?** Check [COLLECTION_SEPARATION_ARCHITECTURE.md](COLLECTION_SEPARATION_ARCHITECTURE.md)

---

## 🎓 Learning Path

### Beginner
1. FRONTEND_CHANGES_QUICK_GUIDE.md
2. Check one code example from FRONTEND_CODE_MIGRATION_EXAMPLES.md
3. Update your types and done!

### Intermediate
1. FRONTEND_CHANGES_QUICK_GUIDE.md
2. Review all examples in FRONTEND_CODE_MIGRATION_EXAMPLES.md
3. SOCIAL_ASSET_FRONTEND_INTEGRATION.md (optional)

### Advanced
1. COLLECTION_SEPARATION_ARCHITECTURE.md
2. COLLECTION_SEPARATION_IMPLEMENTATION.md
3. All frontend docs
4. Backend source code review

---

## 📈 Timeline

- **Reading & Understanding:** 20-30 min
- **Code Updates:** 30-60 min
- **Testing:** 30-45 min
- **Total:** 1.5-2 hours

---

## ✨ Summary

✅ Backend is ready
✅ API is backward compatible
✅ Frontend changes are minimal
✅ File size limit increased to 50MB
✅ Code is cleaner with consistent naming

**You're good to go!** 🚀
