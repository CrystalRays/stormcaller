# StormCaller

Core BGP Simulator

forked from https://github.com/pendgaft/stormcaller


## Data you need

| FilePath | Contents | Download Link |
| - | - | - |
| conf/as_rel.txt | AS Relationship | https://publicdata.caida.org/datasets/as-relationships/serial-1/
| routeViews/readable.txt | BGPdump from MRT format RIB file | http://archive.routeviews.org/ |

## Launch Steps

1. download .bz2 MRT RIB file the same Data with your as_rel.txt from http://archive.routeviews.org/ to routeViews folder
2. run
   ```bash
    cd routeViews
    ./gen.sh
    ```
3. run src/liveView/FileShiv.java
4. run src/sim/SimDriver.java
5. run src/liveView/LiveViewDriver.java
6. Enjoy!
