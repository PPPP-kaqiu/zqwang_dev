# FlashPick Offline Debug Environment

This directory is used for debugging FlashPick's AI features on a PC.

## Directory Structure

- **raw_data/**: Stores raw media files synced from the mobile device.
  - `videos/`: Screen recordings.
  - `audio/`: Voice notes.
  - `images/`: Extracted keyframes or screenshots.

- **ai_input/**: Stores metadata or prompts needed for AI analysis.
  - `prompts.txt`: Current prompts used in the app.
  - `metadata.json`: Extracted metadata from videos.

- **ai_output/**: Stores AI generation results.
  - `database_dump.json`: Dump of the mobile database (titles, summaries).
  - `logs/`: Analysis logs.

- **scripts/**: Scripts for syncing data and running offline analysis.

## Usage

1. Connect your phone via ADB.
2. Run the sync script (to be created) to pull data from `Android/data/com.flashpick.app/files/Movies/FlashPick`.
3. Use the data in `raw_data` to test your VLM prompts or analysis logic.

