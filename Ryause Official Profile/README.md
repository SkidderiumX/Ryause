# Ryause Official Profiles

Official profile repository for the **Ryause** 1.8.9 Forge client.

The client reads `index.json`, lists the profiles below, and can download any of them
into `keystrokes/profiles/` and apply it in-game.

## Layout

```
index.json
profiles/
    Blatant.json
    Closest.json
    SemiBlatant.json
```

## index.json

| Field | Type | Meaning |
| --- | --- | --- |
| `schema` | int | Index format version. Currently `1`. |
| `name` | string | Display name of the repository. |
| `client` | string | Client build the profiles were made for. |
| `mc` | string | Target Minecraft version. |
| `updated` | string | Last update date (`YYYY-MM-DD`). |
| `profiles[]` | array | One entry per downloadable profile. |

Each entry in `profiles[]`:

| Field | Type | Meaning |
| --- | --- | --- |
| `file` | string | File name inside `profiles/` **without** `.json`. Must be 1–14 chars, letters/digits/space/`_`/`-` only. |
| `title` | string | Name shown in the client UI. |
| `author` | string | Profile author. |
| `description` | string | Short description shown before applying. |
| `mc` | string | Target Minecraft version. |
| `sha1` | string | Lowercase SHA-1 of the profile file, used for integrity checks. |

The `file` value becomes the local profile name, so it must stay unique and within the
client's 14-character limit. Use `title` for anything longer.

## Adding a profile

1. Export the profile in-game (`Manager` → `Create profile`).
2. Copy it to `profiles/<name>.json`, keeping the file name ≤ 14 characters.
3. Add an entry to `index.json` and fill in the SHA-1:

   ```powershell
   Get-FileHash profiles\<name>.json -Algorithm SHA1
   ```

4. Update the top-level `updated` field and open a pull request.

## Notes

Profiles only contain module states, settings, keybinds and HUD positions. They cannot
install scripts or execute code.
