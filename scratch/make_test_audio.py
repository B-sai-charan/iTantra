import subprocess
import os

ps_code = """
Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.Rate = 0
$synth.Volume = 100
$synth.SetOutputToWaveFile('scratch\\hello_from_itantra.wav')
$synth.Speak('Hello from iTantra')
$synth.Dispose()
"""

with open("scratch/make_wav.ps1", "w", encoding="utf-8") as f:
    f.write(ps_code)

res = subprocess.run(["powershell", "-ExecutionPolicy", "Bypass", "-File", "scratch/make_wav.ps1"], capture_output=True, text=True)
print("Result:", res.returncode, res.stdout, res.stderr)
if os.path.exists("scratch/hello_from_itantra.wav"):
    print("WAV created, size:", os.path.getsize("scratch/hello_from_itantra.wav"))
