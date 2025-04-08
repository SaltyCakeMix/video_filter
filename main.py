import subprocess
import sys
import os
import time

# Things needed to get this script running:
# 1. Install the above Python packages
# 2. Copy ffmpeg.exe to the directory where python.exe is
# 3. Provide path to a video WITH audio channel(s)
# 4. Download opencv
# 5. Provide the proper path to opencv.jar and its native library
# 6. (RECOMMENDED) If running thru shell, turn off Quick Edit

# Inputs
source = 'src.mp4'
renderHeight = '60'         # Determines rendering resolution, in characters.
outputHeight = '1080'       # Determines the output resolution, in pixels. Aspect ratio copies src.
fontName = 'MonospaceTypewriter.ttf'    # Must be .ttf or .otf
charSet = ' .-^:~/*+=?%##&$$@@@@@@@@@@@@'
outPath = 'out.mkv'         # Should be .mkv
compression = True
cleanup = True

# Input checking
vPath = 'v.mp4'
interVPath = 'i.mp4'
if not os.path.isfile(source):
    sys.exit(f'Source file {source} is not found.')
 
# Processes video using java file
print('Processing video...')
try:
    openCVPath = '.;C:/Users/aweil/Documents/opencv/build/java/opencv-460.jar'
    nativeLibraryPath = '-Djava.library.path=C:/Users/aweil/Documents/opencv/build/java/x64'
    subprocess.run(f'javac -cp "{openCVPath}" VideoFilter.java', shell=True, check=True)
    subprocess.run(['java', nativeLibraryPath, '-cp', openCVPath, 'VideoFilter', source, renderHeight, outputHeight, fontName, charSet, vPath], check=True)
except:
    sys.exit('The video could not be processed. This... might be complicated.')
print('Done.\n')

# Merges video with audio
start = time.time()
print('Compressing video...')
try:
    if compression:
        subprocess.run(f'ffmpeg -i "{vPath}" -i "{source}" -map 0:v -map 1:a -crf 24 "{interVPath}" -y',
                       shell=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    else:
        subprocess.run(f'ffmpeg -i "{vPath}" -i "{source}" -map 0:v -map 1:a -c copy "{interVPath}" -y',
                       shell=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
except:
    sys.exit('Video could not be merged with audio.')
print(f'{time.time() - start} seconds elapsed')
print('Done.\n')

# Extracts subtitles
subtitles = len(subprocess.run(f'ffprobe -v error -select_streams s -show_entries stream=index -of csv=p=0 "{source}"',
                               shell=True, check=True, stdout=subprocess.PIPE, encoding='utf-8').stdout.split('\n')) - 1 # There's always an extra \n at the end
if subtitles > 0:
    start = time.time()
    print('Extracting subtitles...')
    try:
        for i in range(subtitles):
            subprocess.run(f'ffmpeg -i "{source}" -map 0:s:{i} {i}.ass -y', shell=True, check=True,
                           stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    except:
        sys.exit('Could not extract subtitles.')
    print(f'{time.time() - start} seconds elapsed')
    print('Done.\n')

# Combines video, audio, and subtitles, and re-adds fonts
    start = time.time()
    print('Combining components...')
    try:
        args = ['ffmpeg', '-i', interVPath, '-i', source]
        for i in range(subtitles):
            args += ['-i', f'{i}.ass']
        args += ['-map', '0:v', '-map', '0:a']
        for i in range(subtitles):
            args += ['-map', f'{i + 2}:s']
        args += ['-map', '1:t', '-c', 'copy', outPath, '-y']
        subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    except:
        sys.exit('Could not extract or merge subtitles.')
    print(f'{time.time() - start} seconds elapsed')
    print('Done.\n')
else:
    subprocess.run(f'ffmpeg -i "{interVPath}" -c copy "{outPath}" -y',
                   shell=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)

# Remove temp files
if cleanup:
    start = time.time()
    print('Removing temp files...')
    tempFiles = [vPath, interVPath]
    for i in range(subtitles):
        tempFiles += [f'{i}.ass']
    for file in tempFiles:
        if os.path.isfile(file):
            os.remove(file)
    print(f'{time.time() - start} seconds elapsed')
    print('Done.\n')

print('Script finished.')
