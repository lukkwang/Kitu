import torch
from torch.utils.mobile_optimizer import optimize_for_mobile
from ultralytics import YOLO

# 1. Load the model and perform initial export to standard TorchScript format
model = YOLO("lite_yolo.pt")
export_path = model.export(format="torchscript") 
# (Enter the output .torchscript or .pt file path below. It is usually generated as 'yolov8n.torchscript')

# 2. Reload the generated file into a structure dedicated to the PyTorch Mobile Interpreter
print(f"Loading exported file from: {export_path}")
script_module = torch.jit.load(export_path)
script_module.eval()

# 3. Perform mobile optimization and force re-save as a .ptl file containing bytecode.pkl metadata
optimized_module = optimize_for_mobile(script_module)
mobile_output_path = "yolo_android.ptl"
optimized_module._save_for_lite_interpreter(mobile_output_path)

print(f"🎉 Successfully completed! Lightweight model for Android created: {mobile_output_path}")
