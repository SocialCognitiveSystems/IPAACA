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

import logging
import sys
import time

import ipaaca

def my_update_handler(iu, event_type, local):
	print(event_type+': '+str(iu))

if len(sys.argv)<2:
	print "Please use the program as follows:"
	print " "+sys.argv[0]+" <categoryname> [<payloadkey> <payloadvalue>] [<k2> <v2>] ..."
	sys.exit(1)

cate = sys.argv[1]
idx=2
pl={}
while len(sys.argv)>idx+1:
	pl[sys.argv[idx]] = sys.argv[idx+1]
	idx+=2

print "Sending Message of category "+cate
print " with payload "+str(pl)

ob = ipaaca.OutputBuffer('IUInjector')
ob.register_handler(my_update_handler)
iu_top = ipaaca.Message(cate)
iu_top.payload = pl
ob.add(iu_top)
print "Message sent."

time.sleep(0.3)
print "done."

