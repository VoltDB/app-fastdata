/*
 * This file is part of VoltDB.
 * Copyright (C) 2008-2018 VoltDB Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

package events;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Utils {
    public static String itoip(int ip) throws UnknownHostException
    {
        return InetAddress.getByAddress(new byte[] {
                (byte)((ip >>> 24) & 0xff),
                (byte)((ip >>> 16) & 0xff),
                (byte)((ip >>>  8) & 0xff),
                (byte)((ip       ) & 0xff)
        }).getHostAddress();
    }

    public static int iptoi(String ip) throws UnknownHostException
    {
        final byte[] p = InetAddress.getByName(ip).getAddress();
        return ((((int) p[0] << 24) & 0xff000000) |
                (((int) p[1] << 16) & 0x00ff0000) |
                (((int) p[2] <<  8) & 0x0000ff00) |
                (((int) p[3]      ) & 0x000000ff));
    }
}
