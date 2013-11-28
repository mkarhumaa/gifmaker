#!/usr/bin/env python
import glob
import re
import shutil

# End of line
EOL = "\n" # <BR> fixaa 

# UPPER - Static
HTML_UPPER = """
<!DOCTYPE html>
<html>
  <head>
  <title>The GifMakers' collection</title>
  <link href="_CSS_" rel="stylesheet" type="text/css" media="screen" />
  </head>
  <body>
	<section id="mainwrapper">
	<h1>_TOPIC_</h1>
"""

# ELEMENT eg.	
#	_PNG_	= 00X.png
#	_NUMBER_= running number from 1,2,3..
#	subtitle= from 00X.txt file
HTML_ELEMENT = """
		<div id="box-_NUMBER_" class="box">
			<img id="image-_NUMBER_" src="_PNG_" />
			<span class="caption fade-caption cap-_NUMBER_">
			<p>_SUBTITLE_</p>
		</span>
		</div>
"""

INDEX_ELEMENT = """
		<div id="box-3" class="box">
			<img id="image-3" src="_PNG_" />
			<a href="_PATH_">
			<span class="caption fade-caption cap-_NUMBER_">
			<p>_SUBTITLE_</p>
			</span>
			</a>
		</div>
"""



# LOWER - Static
HTML_LOWER = """
	</section>
  </body>
 </html>
"""

CSS_ITEM = """
#mainwrapper .box .cap-_NUMBER_{
	background-color: rgba(0,0,0,0.8);
	background-image: url("./_FOLDER_/master.gif") ;
	background-size: 100% auto;
	background-repeat: no-repeat;
	position: absolute;
	color: #fff;
	z-index: 100;
		-webkit-transition: all 300ms ease-out;
		-moz-transition: all 300ms ease-out;
		-o-transition: all 300ms ease-out;
		-ms-transition: all 300ms ease-out;	
		transition: all 300ms ease-out;
	left: 0;
}
"""




dirs = glob.glob("*")

index_missing = []

#--#
for dir in range(0,len(dirs)):
	# Check for mastergif file. Glob checks for both folders and files. 
	if not glob.glob("%s/master.gif" %(dirs[dir])):
		continue
	if not glob.glob("%s/index.html" %(dirs[dir])):
		index_missing.append(dirs[dir])
	
	# TODO folders:
# print index_missing	
#--#

# Create index.html
for folder in index_missing:
	print folder
	
	# Find .gif(s) and .png(s)
	gif_files =	glob.glob("%s/*" %(folder) + '[0-9]' + "*.gif" )
	
	# print gif_files
	
	# Mapping	
	# SRT - *.txt file
	elements = []
	for i in range(0, len(gif_files)):
		png_file = gif_files[i].replace(".gif",".png")
		txt_file = gif_files[i].replace(".gif",".txt")
		gif_file = gif_files[i]
		
		# add try:
		# print txt_file
		with open(txt_file, mode='r') as f:
			subtitle = f.read()
			# subtitle = subtitle.encode("utf8","ignore")
			# print "READ"
		# print subtitle
		
		# Replace placeholders
		element = HTML_ELEMENT.replace("_SUBTITLE_",subtitle).replace("_NUMBER_","%i" %(i+1)).replace("_PNG_",png_file.replace("%s\\" %folder, ""))	
		elements.append(element)
		
		
	# Combine file
	items = EOL.join(elements)
	index = HTML_UPPER.replace("_CSS_", "gif.css").replace("_TOPIC_", "/%s" %folder)+items+HTML_LOWER

	# Write index.html to folder
	with open("%s/index.html" %folder, 'w') as f:
		f.writelines(index)
		
	# Copy css.file to folder - # src, dst
	shutil.copyfile("./subfolder_template.css", "%s/gif.css" %folder)	
	

# Update master-index	
dirs = glob.glob("*")
indexes = []		
		
for dir in range(0,len(dirs)):
	# Look for index files. 
	if glob.glob("%s/index.html" %(dirs[dir])):
		indexes.append(dirs[dir])

print indexes
		
# for gifs in css		
cap_counter = 0
		
elements = []


	
for folder in indexes:
	print folder
	
	cap_counter += 1
	# Number of gifs in folders without mastergif
	gif_count = len(glob.glob("%s/*.gif" %(folder))) - len(glob.glob("%s/master.gif" %(folder)))
	print gif_count
	
	subtitle = "Show: %s " %folder + EOL + "This show contains %i gif(s)."% gif_count + EOL + "Click to open."
	master_png = "./%s/master.png" %folder
	path = "./%s/index.html" %folder
	
	
	element = INDEX_ELEMENT.replace("_SUBTITLE_",subtitle).replace("_PNG_", master_png).replace("_PATH_", path).replace("_NUMBER_", "%i" % cap_counter)
	elements.append(element)
	
css_items = []
cap_counter = 0



# Read css style
with open("style_template.css", mode='r') as f:
	stylesheet = f.read()

for folder in indexes:
	cap_counter += 1
	css_replaced = CSS_ITEM.replace("_NUMBER_", "%i" % cap_counter).replace("_FOLDER_", folder)
	css_items.append(css_replaced)
	
# print css_items	
css_stuff = EOL.join(css_items)
css_style = stylesheet.replace(r"/*ITEMS*/", css_stuff)
	
# print css_style	

# Combine file
items = EOL.join(elements)
index = HTML_UPPER.replace("_CSS_", "style.css").replace("_TOPIC_", "/HOME")+items+HTML_LOWER

with open("index.html", 'w') as f:
	f.writelines(index)

with open("style.css", 'w') as f:
	f.writelines(css_style)

	
