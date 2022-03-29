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

package com.immersive.annotations;


public interface TransactionalEntity<T extends DataModelEntity> extends DataModelEntity {
    T getOwner();
}
