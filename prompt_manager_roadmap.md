# Feature Specification & Roadmap: Prompt Manager

- **Branch Name:** `feature/prompt-manager`
- **Scope:** Dedicated UI and local persistence engine allowing users to create, store, organize, edit, import, and export reusable prompt templates for interactions with Jules.
- **Implementation Strategy:** Local database persistence with frictionless plain-text (`.txt`) file interoperability and clipboard-centric workflows.

---

## 1. Overview & Core Objectives

1. **Quick-Copy Access:** Minimize friction when working across multiple Jules sessions by enabling single-tap copying of prompt templates directly to the system clipboard.
2. **Robust Persistence:** Store records within an indexed local database rather than relying solely on file system links, avoiding OS file-lock race conditions, special character encoding issues, and filesystem sanitization constraints.
3. **Seamless File Interoperability:** Support importing `.txt` files directly into the database as records, and exporting database records back to the local device or external drives as `.txt` files.
4. **Clean Management Workflows:** Support creation, renaming, in-depth editing, system share-sheet dispatching, and secure deletion with confirmation prompts.

---

## 2. Database & Data Architecture

### 2.1 Entity Schema: `saved_prompts`

| Field Name | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | Integer / UUID | Primary Key, Auto-increment | Unique system identifier for the prompt record. |
| `title` | Text | Non-Null | Display title chosen by the user or derived from imported filename. |
| `content` | Text | Non-Null | The full body/string of the prompt. |
| `created_at` | Timestamp / Long | Non-Null | Epoch timestamp recorded at creation. |
| `updated_at` | Timestamp / Long | Non-Null | Epoch timestamp updated whenever `title` or `content` changes. |
| `last_used_at`| Timestamp / Long | Nullable | Optional timestamp updated whenever tapped/copied; allows "recent" sorting. |
| `sort_order` | Integer | Default 0 | Optional manual ordering index if drag-and-drop ordering is introduced. |

### 2.2 Title & Filename Decoupling
- The `title` column supports spaces, special characters, and non-ASCII characters without filesystem restrictions.
- When exporting, the application converts the database `title` into a sanitized filename string (`<safe_title>.txt`) to ensure cross-platform compatibility.

---

## 3. UI Layout & Component Architecture

### 3.1 Header / Top App Bar
- **Title:** "Prompt Manager" (or "Prompt Library").
- **Search Bar / Filter Action:** Collapsible search field matching queries in real time against both `title` and `content`.
- **Batch Actions Trigger (Optional):** Toggle for multi-selection mode (batch deletion or batch archive export).

### 3.2 Main Prompt List
- **List Presentation:** Vertical scrollable list of card or row items.
- **Item Row Components:**
  - **Title:** Primary text in bold/headline styling.
  - **Content Snippet:** Secondary text showing a 1–2 line truncated preview of `content`.
  - **Timestamp / Metadata:** Subtle display of last updated or last used date.
  - **Overflow Trigger:** 3-dot vertical icon providing secondary access to the context menu (improving discoverability over long-press only).

### 3.3 Gestures & Direct Interactions
- **Single Tap:**
  - Copies `content` directly to the system clipboard.
  - Emits light haptic feedback.
  - Updates `last_used_at` timestamp.
  - Displays a transient confirmation (e.g., Toast or Snackbar: *"Copied '[title]' to clipboard"*).
- **Long Press (or 3-Dot Tap):**
  - Surfaces a contextual Bottom Sheet or Action Menu displaying:
    1. **Edit:** Opens full-screen or dialog-based template editor.
    2. **Rename:** Opens a lightweight, single-input dialog.
    3. **Share:** Launches system share sheet with MIME type `text/plain`.
    4. **Export:** Opens system document picker to write `.txt` file.
    5. **Delete:** Opens confirmation modal.

### 3.4 Floating Action Button (FAB) Speed Dial
- Located in the bottom-right corner.
- Expands into two distinct entry points:
  1. **New Prompt:** Launches a blank prompt editor.
  2. **Import Prompts:** Invokes the system file picker to select one or multiple `.txt` files.

---

## 4. Workflows & State Specifications

### 4.1 Create & Edit Workflow
- **Modal / Editor View:**
  - Field 1: `Title` (Single-line input, auto-focused for new prompts).
  - Field 2: `Content` (Multi-line text editor with vertical scrolling).
- **Validation Rules:**
  - `Title` must not be blank; if left empty, defaults to *"Untitled Prompt - YYYY-MM-DD"*.
  - `Content` must contain at least one non-whitespace character to enable the Save button.
- **Unsaved Changes Prompt:** If user attempts to dismiss the editor with modified text, present a *"Discard changes?"* confirmation dialog.

### 4.2 Rename Workflow
- Lightweight dialog prompting for a new `title`.
- Pre-filled with current title text, selected on open.
- Actions: **Cancel** and **Save**.

### 4.3 Delete Workflow
- Confirmation dialog:
  - Header: *"Delete Prompt?"*
  - Body: *"Are you sure you want to delete '[Title]'? This action cannot be undone."*
  - Actions: **Cancel** (dismisses) and **Delete** (destructive styling, removes record from database).

### 4.4 System Share Sheet Dispatch
- Dispatches standard plain-text intent (`ACTION_SEND` with `text/plain`).
- Payload includes `content` as the primary text extra, with `title` assigned to the subject line extra.

### 4.5 Import Workflow (`.txt`)
- Triggers system document picker (`ACTION_OPEN_DOCUMENT` or equivalent `GetMultipleContents`) filtered to `text/plain`.
- **Parsing:** Iterates through selected files:
  - Base filename (minus extension) is mapped to `title`.
  - Stream contents are read as UTF-8 and mapped to `content`.
- **Collision Strategy:** Multiple imports with identical titles are permitted since records have unique database IDs. The system may append an auto-increment tag (e.g., `Prompt (1)`) for user clarity.
- **Feedback:** Displays summary toast (e.g., *"Imported 4 prompts"*).

### 4.6 Export Workflow (`.txt`)
- **Single Prompt Export:**
  - Invokes system file creator (`ACTION_CREATE_DOCUMENT`) with suggested name `<sanitized_title>.txt`.
  - Streams record `content` to the user-chosen target URI.
- **Bulk Export (Future Phase):**
  - Option A: Write all prompts into a user-selected folder via directory tree picker (`ACTION_OPEN_DOCUMENT_TREE`).
  - Option B: Compress all records into a single `.zip` containing `<sanitized_title>.txt` files.

---

## 5. Phased Delivery Roadmap

### Phase 1: Persistence & Core List View
- [ ] Initialize branch `feature/prompt-manager`.
- [ ] Define local database schema and migration scripts for `saved_prompts`.
- [ ] Construct the primary list view screen with FAB and search filter bar.
- [ ] Implement single-tap clipboard copy action with toast and haptic feedback.

### Phase 2: CRUD Modals & Context Menus
- [ ] Build contextual bottom sheet / overflow menu (Rename, Edit, Share, Export, Delete).
- [ ] Build Create / Edit dialog with non-empty validation and unsaved changes prompt.
- [ ] Build Rename modal and Delete confirmation dialog.
- [ ] Implement system share intent dispatch.

### Phase 3: File System Interop (Import & Export)
- [ ] Implement single-item export using system document creation flow.
- [ ] Implement single/multi-file `.txt` import parsing via file picker.
- [ ] Add filename sanitization logic and title deduplication checks.

### Phase 4: Polish & Refinement
- [ ] Verify edge cases (massive text handling, memory truncation in list cards).
- [ ] Implement list sorting options (Alphabetical, Recently Used, Date Created).
- [ ] Test system clipboard compatibility across Android versions.