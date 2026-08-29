# ItemOwners - /own items and view their history

ItemOwners is a plugin that allows players to own items and view an items history.
- Every event on the item, such as storing the item in a chest, destroying, breaking, and everything between is tracked and stored for a configurable duration of time.
- The newest event for each item is retained indefinitely as its last known state, even after older detailed history expires.
- Players can (configurable by permissions) view their own items to track down what has happened to it, such as if they had given the item to another player and it was lost.
- Moderators or admins can (configurable by permissions) view all items and their events.
- Owners can recover an item after a confirmed despawn by paying a configurable Vault economy fee.

## Images
#### An owned item
![Owned Item](https://i.imgur.com/jRLZ4kY.jpg)

#### ItemHistory
![ItemHistory](https://i.imgur.com/GgPlfFi.jpg)

Hovering over **[loc]** will show you the location, and hovering over **[pl]** will show you the player that had the item.

#### ItemsOwned
![ItemsOwned](https://i.imgur.com/H0Q70VM.jpg)

Hovering over the Item ID shows the native Minecraft item tooltip, including its display name, formatted lore, visible enchants, flags, and metadata. Clicking an ID in `/itemsowned` opens that item's history directly. Multi-page `/itemsowned` and `/itemhistory` results include clickable Previous and Next controls; page numbers can still be supplied in the command.



## Supported Versions
- Paper **1.21 or newer** with Java **21 or newer**. The current build is smoke-tested on Paper **26.1.2**.

## Recovery safety

Paid recovery requires Vault plus a registered economy provider; a fee of zero works without Vault. Only confirmed, uncancelled despawns recorded after the recovery feature is installed are eligible; older history rows are not backfilled because they do not contain an exact despawn-time snapshot or a one-time recovery claim.

The fee and recovery window are configured under `recovery` in `config.yml`. If two different entities with the same Item ID despawn before payment starts, automatic recovery is locked for staff review and no fee is charged. Once payment has entered its persisted point-of-no-return state, later duplicate losses are rejected without changing that committed recovery.

Item identity in this legacy plugin is still based on its tracking lore. This is suitable for a normal survival server where players cannot edit or clone arbitrary item NBT. If another plugin, creative access, or an exploit lets players forge full item data, keep paid recovery disabled until item identities are upgraded to server-authenticated, rotating tokens.

## Commands
- ***/own***
  
  **Description:** *Own an item that is in your hand*
  
  **Permissions:** 
    - *ItemOwners.own - Own items*


- ***/disown***
  
  **Description:** *Disowns an item that is in your hand*
  
  **Permissions:**
    - *ItemOwners.disown.own - Disown your own items*
    - *ItemOwners.disown.all - Disown **any** item*


- ***/itemhistory <item id> (page)***
  
  **Description:** *View an items history in reverse-chronological order.*
  
  **Permissions:**
    - *ItemOwners.view.own - View history for your own items*
    - *ItemOwners.view.all - View history for **any** item*


- ***/recoveritem <item id>***

  **Description:** *Recover your owned item after a confirmed despawn*

  **Permissions:**
    - *ItemOwners.recover.own - Recover your own despawned items*


- ***/itemsowned <player name> (page)***
  
  **Description:** View the items owned by a specified player.
  
  **Permissions:**
    - *ItemOwners.list.own - View the items you own*
    - *ItemOwners.list.all - View the items **any** player owns*
