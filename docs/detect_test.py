from PIL import Image
import torch

PTL_PATH = "yolov8n_android.ptl"
IMG_PATH = "cycle.png"
IMG_SIZE = 640  # target image size

# 1. .ptl model load
model = torch.jit.load(PTL_PATH, map_location="cpu")
model.eval()

# 2. image open and RGB convert
img = Image.open(IMG_PATH).convert("RGB")
orig_w, orig_h = img.size

# 3. ratio convert and padding
scale = min(IMG_SIZE / orig_w, IMG_SIZE / orig_h)
new_w = int(orig_w * scale)
new_h = int(orig_h * scale)

resized = img.resize((new_w, new_h), Image.Resampling.BILINEAR)
canvas = Image.new("RGB", (IMG_SIZE, IMG_SIZE), (114, 114, 114))
canvas.paste(resized, (0, 0))

# 4. Pure Python byte parsing (NumPy / torchvision 미사용)
pixels = list(canvas.getdata())  # [(R, G, B), ...]
total_pixels = IMG_SIZE * IMG_SIZE

r_channel = [p[0] / 255.0 for p in pixels]
g_channel = [p[1] / 255.0 for p in pixels]
b_channel = [p[2] / 255.0 for p in pixels]

# [1, 3, 640, 640] tensor generate
raw_list = r_channel + g_channel + b_channel
tensor = torch.tensor(raw_list, dtype=torch.float32).reshape(1, 3, IMG_SIZE, IMG_SIZE)

# 5. inference and max class score
with torch.no_grad():
    out = model(tensor)
    if isinstance(out, (tuple, list)):
        out = out[0]

print("output shape:", out.shape)  # score: torch.Size([1, 84, 8400])

cls_scores = out[0, 4:, :]  # shape: [80, 8400]
max_val, flat_idx = torch.max(cls_scores.flatten(), dim=0)
best_class_id = (flat_idx.item() // cls_scores.shape[1])

print(f"PC bestScore={max_val.item():.6f}, bestClassId={best_class_id}")
