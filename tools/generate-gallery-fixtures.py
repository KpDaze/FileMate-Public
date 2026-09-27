"""Only synthetic solid-colour test media. No external packages or personal data."""
import base64, pathlib, struct, sys, zlib
out = pathlib.Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
def png(name, width, height, rgb):
    def chunk(kind, data):
        return struct.pack('>I',len(data)) + kind + data + struct.pack('>I',zlib.crc32(kind+data))
    payload = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,2,0,0,0))
    payload += chunk(b'IDAT',zlib.compress((b'\x00'+bytes(rgb)*width)*height)) + chunk(b'IEND',b'')
    (out/name).write_bytes(payload)
png('Screenshot_FileMateFixture.png',1080,1920,(36,93,175))
png('FileMateFixture_camera.png',640,480,(86,159,115))
png('FileMateFixture_download.png',500,500,(237,183,61))
png('FileMateFixture_changed.png',700,500,(147,81,179))
(out/'FileMateFixture_video.mp4').write_bytes(base64.b64decode(pathlib.Path(__file__).with_name('fixtures').joinpath('gallery-video.mp4.b64').read_bytes()))
