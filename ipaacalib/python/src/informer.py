#!/usr/bin/env python

# This file is part of IPAACA, the
#  "Incremental Processing Architecture
#   for Artificial Conversational Agents".	
#
# Copyright (c) 2009-2013 Sociable Agents Group
#                         CITEC, Bielefeld University	
#
# http://opensource.cit-ec.de/projects/ipaaca/
# http://purl.org/net/ipaaca
#
# This file may be licensed under the terms of of the
# GNU Lesser General Public License Version 3 (the ``LGPL''),
# or (at your option) any later version.
#
# Software distributed under the License is distributed
# on an ``AS IS'' basis, WITHOUT WARRANTY OF ANY KIND, either
# express or implied. See the LGPL for the specific language
# governing rights and limitations.
#
# You should have received a copy of the LGPL along with this
# program. If not, go to http://www.gnu.org/licenses/lgpl.html
# or write to the Free Software Foundation, Inc.,
# 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.	
#
# The development of this software was supported by the
# Excellence Cluster EXC 277 Cognitive Interaction Technology.
# The Excellence Cluster EXC 277 is a grant of the Deutsche
# Forschungsgemeinschaft (DFG) in the context of the German
# Excellence Initiative.


import time
import ipaaca

def remote_change_dumper(iu, event_type, local):
	if local:
		print 'remote side '+event_type+': '+str(iu)


ob = ipaaca.OutputBuffer('CoolInformerOut')
ob.register_handler(remote_change_dumper)

iu_top = ipaaca.IU()
iu_top.payload = {'data': 'raw'}
ob.add(iu_top)

iu = ipaaca.IU()
iu.payload = {'a':'a1'}
ob.add(iu)

iu.payload = {'a':'a2', 'b':'b1'} #OK
del(iu.payload['b'])
iu.payload['c'] = 'c1'
iu.payload['a'] = 'a3'
iu.add_links('sameold', iu_top.uid)

time.sleep(1)
iu.commit()

while True:
	time.sleep(1)


