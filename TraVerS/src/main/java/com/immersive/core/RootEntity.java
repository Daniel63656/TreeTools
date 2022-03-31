/*
 *  Copyright (c)  2020/2021/2021 Peter Brummer AIRAES GmbH
 *  <b>Copyright 2020/2021 Peter Brummer AIRAES GmbH</b><br>
 *
 */

/*
 *  Copyright (c)  2020/2021/2021 Peter Brummer AIRAES GmbH
 *  <b>Copyright 2020/2021 Peter Brummer AIRAES GmbH</b><br>
 *
 */

package com.immersive.core;

public abstract class RootEntity extends DataModelEntity {
    @Override
    LogicalObjectKey[] getConstructorParams(LogicalObjectTree LOT) {
        return new LogicalObjectKey[0];
    }
}