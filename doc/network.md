# Plasmid Networking

Plasmid networking uses the custom payload packets of Minecraft. This document describes the networking with Plasmid.

## Specification

### Packet Specification

In this specification a packet is its own section, 
a description of what the packet is used for and how it should be handled,
then it specifies the direction (`C->S`, `C<-S`, `C<->S`),
and a table of the fields and type of the packet.

## Types

Some data types used in this documentation are documented [here](https://wiki.vg/Protocol#Data_types).

### Bounds

Bounds represents a box which are defined with two [BlockPos]: a minimum and a maximum.

| Fields | Type       | Description             |
|:------:|:----------:|:------------------------|
| min    | [BlockPos] | The minimum coordinate. |
| max    | [BlockPos] | The maximum coordinate. |

### Region

A region represents a named box in a Plasmid map which can contain arbitrary data.

| Fields | Type       | Description                          |
|:------:|:----------:|:-------------------------------------|
| marker | string     | The name of the region.              |
| bounds | [Bounds]   | The bounds of the region.            |
| data   | NBT Tag    | Arbitrary data stored in the region. |

### GameProfile

GameProfile represents a player.

| Fields     | Type                       | Description             |
|:----------:|:--------------------------:|:------------------------|
| uuid       | UUID                       | The UUID of the player. |
| name       | string (16)                | The name of the player. |
| properties | [GameProfile.Property]\[\] | The properties.         |

### GameProfile.Property

GameProfile.Property represents a named property with an assigned value, it can also be signed.

| Fields    | Type              | Description                     |
|:---------:|:-----------------:|:--------------------------------|
| name      | string (32767)    | The name of the property.       |
| value     | string (32767)    | The value of the property.      |
| signed    | bool              | `true` if signed, else `false`. |
| signature | (string (32767))? | Only if `signed` is `true`.     |

### Array `A[]`

An array holds a list of values of the specified type.
It also contains the size `N` of the array.

| Fields | Type     | Description                                 |
|:------:|:--------:|:--------------------------------------------|
| size   | [VarI32] | The size of the array.                      |
| 0      | A        | The first element of the array if present.  |
| 1      | A        | The second element of the array if present. |
| ...    | A        | An element of the array if present.         |
| N-1    | A        | The N-1 element of the array if present.    |

### Tuple `(A, B, ...)`

A tuple is a type which holds the content of multiple types specified in its name.
The tuple type is very abstract and acts as many fields as the tuple should hold.

Size: size of `A` + size of `B` + ...

| Fields | Type | Description                    |
|:------:|:----:|:-------------------------------|
| first  | `A`  | The first value of the tuple.  |
| second | `B`  | The second value of the tuple. |
| ...    | ...  | Another value of the tuple.    |

### Optional `X?`

An optional is a value which may or may not be present.

Size: 0 if non-present, else size of `X`.

## Packets

### Game-related Packets

#### `plasmid:game/player_add`

Packet sent to all players of a game space when a player is joined to a game space.
The player that was joined to the game space also receives this packet.

Direction: `C<-S`

| Fields         | Type       | Description                                                     |
|:--------------:|:----------:|:----------------------------------------------------------------|
| game_type_id   | Identifier | The identifier of the game type.                                |
| game_type_name | Text       | The name of the game type.                                      |
| game_id        | Identifier | The identifier of the game.                                     |
| game_name      | Text       | The name of the game.                                           |
| player_count   | int        | The new player count of the game space after the player joined. |
| player_uuid    | UUID       | The UUID of the player that joined the game space.              |

#### `plasmid:game/player_removed`

Packet sent to all players of a game space when a player is removed from a game space.
The player that was removed from the game space also receives this packet.

Direction: `C<-S`

| Fields         | Type       | Description                                                         |
|:--------------:|:----------:|:--------------------------------------------------------------------|
| game_type_id   | Identifier | The identifier of the game type.                                    |
| game_type_name | Text       | The name of the game type.                                          |
| game_id        | Identifier | The identifier of the game.                                         |
| game_name      | Text       | The name of the game.                                               |
| player_count   | int        | The new player count of the game space after the player is removed. |
| player_uuid    | UUID       | The UUID of the player that was removed from the game space.        |

#### `plasmid:game/game_close`

Packet sent to all players in a game space when it closes.

Direction: `C<-S`

| Fields         | Type            | Description                        |
|:--------------:|:---------------:|:-----------------------------------|
| game_type_id   | Identifier      | The identifier of the game type.   |
| game_type_name | Text            | The name of the game type.         |
| game_id        | Identifier      | The identifier of the game.        |
| game_name      | Text            | The name of the game.              |
| reason         | GameCloseReason | The reason for the game's closure. |

### Workspace-related Packets

The server controls most of the requests sent by a client for workspace-related packets as it requires some permissions.
Those packets are most likely sent only on build servers.

#### `plasmid:workspace/enter`

Packet sent when a player enters a workspace editor. The client should render the bounds of the map if present,
and the contained regions, points, etc.  
A `plasmid:workspace/regions` packet should be sent with this packet if the workspace contains regions.

Direction: `C<-S`

| Fields    | Type       | Description                                                            |
|:---------:|:----------:|:-----------------------------------------------------------------------|
| workspace | Identifier | The identifier of the entered workspace.                               |
| bounds    | [Bounds]   | The bounds of the map, may be `[[0, 0, 0], [0, 0, 0]]` if not present. |
| world     | Identifier | The world in which the workspace is present.                           |
| data      | NBT Tag    | Arbitrary data assigned to the map template.                           |

#### `plasmid:workspace/new`

Sent by the client to request the server to create a new workspace. 

Direction: `C->S`

| Fields    | Type           | Description                                                            |
|:---------:|:--------------:|:-----------------------------------------------------------------------|
| workspace | Identifier     | The identifier of the new workspace.                                   |
| bounds    | [Bounds]       | The bounds of the map, may be `[[0, 0, 0], [0, 0, 0]]` if not present. |
| generator | string (32767) | The generator string for the map, may be empty if unspecified.         |
| data      | NBT Tag        | Arbitrary data assigned to the map template.                           |

#### `plasmid:workspace/leave`

Leaves the specified workspace.

Direction: `C<->S`

| Fields    | Type       | Description                           |
|:---------:|:----------:|:--------------------------------------|
| workspace | Identifier | The identifier of the left workspace. |

#### `plasmid:workspace/bounds`

Sets the bounds of the workspace map. The client should render those bounds if non-null.
A client can request bounds change, but can be rejected. If a request is accepted, the packet is sent back to the client.

Direction `C<->S`

| Fields    | Type       | Description                                                                      |
|:---------:|:----------:|:---------------------------------------------------------------------------------|
| workspace | Identifier | The identifier of the affected workspace.                                        |
| bounds    | [Bounds]   | The new bounds of the workspace map, `[[0, 0, 0], [0, 0, 0]]` if bounds is null. |

#### `plasmid:workspace/data`

Sets the associated data on the workspace map.
A client can request data change, but can be rejected. If a request is accepted, the packet is sent back to the client.

Direction: `C<->S`

| Fields    | Type       | Description                               |
|:---------:|:----------:|:------------------------------------------|
| workspace | Identifier | The identifier of the affected workspace. |
| data      | NBT Tag    | The NBT data.                             |

#### `plasmid:workspace/region`

Specifies a single region, the client should render a box, and a marker tag for it.
If sent from a client, this is considered as an update request which can be rejected.

Direction: `C<->S`

| Fields     | Type       | Description                    |
|:----------:|:----------:|:-------------------------------|
| runtime_id | [VarI32]   | The region runtime identifier. |
| region     | [Region]   | The region to add or update.   |

#### `plasmid:workspace/regions`

Specifies regions to the client, for each region the client should render a box, and a marker tag.

Direction: `C<-S`

| Fields    | Type                              | Description                                                       |
|:---------:|:---------------------------------:|:------------------------------------------------------------------|
| marker    | string                            | The marker of the regions to update.                              |
| regions   | ([VarI32], [Bounds], NBT Tag)\[\] | The array of regions to update. The tuple is, in order, `(runtime id, bounds, data)`. |

#### `plasmid:workspace/region/add`

Requests the server to add a new region in the current workspace.
The client should not assume the region exists until the server sends a `plasmid:workspace/region` packet.

Direction: `C->S`

| Fields    | Type       | Description                                                          |
|:---------:|:----------:|:---------------------------------------------------------------------|
| region    | [Region]   | The region to add.                                                   |

#### `plasmid:workspace/region/remove`

Remove a region from the current workspace.
When sent from a client, this packet should be treated as a request which can be rejected.

Direction: `C<->S`

| Fields     | Type       | Description                                                            |
|:----------:|:----------:|:-----------------------------------------------------------------------|
| runtime_id | [VarI32]   | The region runtime ID.                                                 |

### Environment Packets

Those packets can be seen as an extension to reduce the bandwidth usage, like particles which have a start and end packet
to avoid continuously send particle packets.

Those packets mostly affects the environment/are cosmetics.

#### `plasmid:env/particle`

Sends an environment particle to the client.
An environment particle will loop through until the client receives a stop packet unless the packet specifies a loop count.

Direction: `C<-S`

| Fields        | Type       | Description                                                                       |
|:-------------:|:----------:|:----------------------------------------------------------------------------------|
| id            | [VarI32]   | The runtime ID of the particle source.                                            |
| duration      | [VarI32]   | Duration in ticks of a loop.                                                      |
| offset        | u8         | Bounds of a duration offset to randomize the duration of loops, 0 if unspecified. |
| loops         | [VarI32]   | The maximum number of loops to do, 0 is indefinite and requires a stop packet.    |
| ...           | ...        | [Content of the Particle packet.](https://wiki.vg/Protocol#Particle_2)            |

#### `plasmid:env/particle/stop`

Tells the client to stop emitting the specified environment particle.

Direction: `C<-S`

| Fields        | Type         | Description                                                                    |
|:-------------:|:------------:|:-------------------------------------------------------------------------------|
| ids           | [VarI32]\[\] | The IDs of the particle sources to remove.                                     |

[VarI32]: https://wiki.vg/Protocol#VarInt_and_VarLong "wiki.vg documentation"
[BlockPos]: https://wiki.vg/Protocol#Position "wiki.vg documentation"
[Bounds]: #bounds
[Region]: #region
[GameProfile]: #gameprofile
[GameProfile.Property]: #gameprofileproperty
