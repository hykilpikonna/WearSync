package org.hydev.wearsync.bles

import com.welie.blessed.ScanFailure

class ScanException(val fail: ScanFailure) : Exception()
{
}