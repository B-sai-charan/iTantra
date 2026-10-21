import os
from PIL import Image, ImageDraw, ImageOps

source_path = r"C:\Users\saich\Downloads\SIH\PS26173\app_logo.png"
res_dir = r"C:\Users\saich\Downloads\SIH\PS26173\app\src\main\res"

img = Image.open(source_path).convert("RGBA")

# Dimensions for standard mipmap densities
densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

# Function to create circular/round icon
def make_round(im):
    size = im.size
    mask = Image.new('L', size, 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size[0], size[1]), fill=255)
    output = Image.new('RGBA', size, (0, 0, 0, 0))
    output.paste(im, (0, 0))
    output.putalpha(mask)
    return output

# 1. Generate mipmap density icons (square and round)
for density, size in densities.items():
    folder = os.path.join(res_dir, density)
    os.makedirs(folder, exist_ok=True)
    
    # Square / standard icon
    resized = img.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(os.path.join(folder, "ic_launcher.png"), "PNG")
    
    # Round icon
    round_icon = make_round(resized)
    round_icon.save(os.path.join(folder, "ic_launcher_round.png"), "PNG")

# 2. Generate Adaptive Icon Foreground for drawable/mipmap
# Android Adaptive Icon canvas is 108dp x 108dp.
# Inner 72dp is the safe viewport (66%).
# At xxxhdpi, canvas is 432x432 px, inner safe area is ~288x288 px.
canvas_size = 432
inner_size = 300  # fits logo beautifully with padding for squircle/circle masks
fg_img = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
logo_resized = img.resize((inner_size, inner_size), Image.Resampling.LANCZOS)
offset = (canvas_size - inner_size) // 2
fg_img.paste(logo_resized, (offset, offset), logo_resized)

# Save foreground to drawable and mipmap-xxxhdpi
drawable_dir = os.path.join(res_dir, "drawable")
fg_img.save(os.path.join(drawable_dir, "ic_launcher_foreground.png"), "PNG")

for density, size in densities.items():
    folder = os.path.join(res_dir, density)
    # Scale foreground for density: 108dp * density_scale
    fg_size = int(size * 108 / 48)  # 48 is base mdpi size
    scaled_fg = fg_img.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
    scaled_fg.save(os.path.join(folder, "ic_launcher_foreground.png"), "PNG")

print("All icon assets generated successfully!")
