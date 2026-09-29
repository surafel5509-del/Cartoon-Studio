# Project Format

Cartoon Studio projects are versioned from the first implementation.

## Logical Structure

project/
  manifest.json
  project.json
  scenes/
  assets/
  audio/
  thumbnails/
  metadata/

## Rules

- Every project has a schema version.
- Assets use stable IDs rather than absolute device paths.
- Cache data is never required to open a project.
- Saves use atomic replacement semantics.
- Migrations are explicit and tested.
- Unknown optional fields should be safely ignored where possible.
