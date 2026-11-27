from ultralytics import YOLO

# model load(light model)
model = YOLO("yolo11n.pt")

# class id and name 
print(f"Total number of class: {len(model.names)}")
for class_id, class_name in model.names.items():
    print(f"{class_id}: {class_name}")

