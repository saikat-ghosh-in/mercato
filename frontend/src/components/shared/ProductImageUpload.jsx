import { useState } from 'react';
import { toast } from 'react-hot-toast';
import { MdClose, MdCloudUpload, MdImage, MdLink } from 'react-icons/md';
import api from "../../backend/api";

export default function ProductImageUpload({
  primaryImage,
  secondaryImages = [],
  onPrimaryChange,
  onSecondaryChange
}) {
  const [uploadingPrimary, setUploadingPrimary] = useState(false);
  const [uploadingSecondary, setUploadingSecondary] = useState(false);
  const [inputMode, setInputMode] = useState('upload');

  const handleUploadImage = async (file, isPrimary = true) => {
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      toast.error('Only JPG, PNG, and WebP images are allowed');
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      toast.error('File size must be less than 5MB');
      return;
    }

    const formData = new FormData();
    formData.append('file', file);

    const setLoading = isPrimary ? setUploadingPrimary : setUploadingSecondary;
    setLoading(true);

    try {
      const { data } = await api.post('/seller/upload-image', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });

      toast.success('Image uploaded');

      if (isPrimary) {
        onPrimaryChange(data.imageUrl);
      } else {
        if (secondaryImages.length < 3) {
          onSecondaryChange([...secondaryImages, data.imageUrl]);
        } else {
          toast.error('Maximum 3 secondary images allowed');
        }
      }
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Upload failed');
    } finally {
      setLoading(false);
    }
  };

  const handlePrimaryFileChange = (e) => {
    const file = e.target.files[0];
    if (file) handleUploadImage(file, true);
    e.target.value = '';
  };

  const handleSecondaryFileChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      if (secondaryImages.length < 3) {
        handleUploadImage(file, false);
      } else {
        toast.error('Maximum 3 secondary images allowed');
      }
    }
    e.target.value = '';
  };

  const handleUrlSubmit = (isPrimary = true) => {
    const inputId = isPrimary ? 'primary-url-input' : 'secondary-url-input';
    const input = document.getElementById(inputId);
    const url = input?.value?.trim();

    if (!url) {
      toast.error('Please enter a valid URL');
      return;
    }

    try {
      new URL(url);
    } catch {
      toast.error('Invalid URL format');
      return;
    }

    if (isPrimary) {
      onPrimaryChange(url);
    } else {
      if (secondaryImages.length < 3) {
        onSecondaryChange([...secondaryImages, url]);
        input.value = '';
      } else {
        toast.error('Maximum 3 secondary images allowed');
      }
    }
  };

  const removeSecondaryImage = (index) => {
    const updated = secondaryImages.filter((_, i) => i !== index);
    onSecondaryChange(updated);
  };

  return (
    <div className="space-y-4">
      {/* Primary Image */}
      <div>
        <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-2">
          Primary Image <span className="text-red-400">*</span>
        </label>

        {/* Toggle between Upload and URL */}
        <div className="flex gap-2 mb-3">
          <button
            type="button"
            onClick={() => setInputMode('upload')}
            className={`control flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-[transform,opacity,background-color] ${inputMode === 'upload'
              ? 'bg-gray-950 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
          >
            <MdCloudUpload size={13} />
            Upload
          </button>
          <button
            type="button"
            onClick={() => setInputMode('url')}
            className={`control flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-[transform,opacity,background-color] ${inputMode === 'url'
              ? 'bg-gray-950 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
          >
            <MdLink size={13} />
            URL
          </button>
        </div>

        {inputMode === 'upload' ? (
          <div>
            {primaryImage ? (
              <div className="relative group">
                <img
                  src={primaryImage}
                  alt="Primary"
                  className="w-full h-48 object-cover rounded-xl border border-gray-200"
                />
                <label
                  htmlFor="primary-upload"
                  className="absolute bottom-3 right-3 bg-gray-950 text-white px-3 py-1.5 rounded-lg cursor-pointer hover:bg-gray-800 transition text-xs font-semibold opacity-0 group-hover:opacity-100"
                >
                  {uploadingPrimary ? 'Uploading...' : 'Change'}
                </label>
              </div>
            ) : (
              <label
                htmlFor="primary-upload"
                className="flex flex-col items-center justify-center w-full h-48 border-2 border-dashed border-gray-300 rounded-xl cursor-pointer hover:border-gray-400 transition-colors"
              >
                <MdCloudUpload className="w-10 h-10 mb-2 text-gray-400" />
                <p className="text-xs text-gray-500 font-semibold">Click to upload</p>
                <p className="text-[10px] text-gray-400 mt-1">PNG, JPG, or WebP (max. 5MB)</p>
              </label>
            )}
            <input
              id="primary-upload"
              type="file"
              accept="image/jpeg,image/jpg,image/png,image/webp"
              onChange={handlePrimaryFileChange}
              disabled={uploadingPrimary}
              className="hidden"
            />
          </div>
        ) : (
          <div className="space-y-2">
            <div className="flex gap-2">
              <input
                id="primary-url-input"
                type="url"
                placeholder="https://example.com/image.jpg"
                defaultValue={primaryImage}
                className="flex-1 text-xs border border-gray-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
              />
              <button
                type="button"
                onClick={() => handleUrlSubmit(true)}
                className="control px-4 py-2 bg-gray-950 text-white rounded-xl hover:bg-gray-800 transition text-xs font-semibold"
              >
                Set
              </button>
            </div>
            {primaryImage && (
              <img
                src={primaryImage}
                alt="Primary preview"
                className="w-full h-40 object-cover rounded-xl border border-gray-200"
              />
            )}
          </div>
        )}
      </div>

      {/* Secondary Images */}
      <div>
        <label className="text-[10px] font-bold text-gray-400 uppercase tracking-widest block mb-2">
          Secondary Images (Optional - Max 3)
        </label>

        <div className="grid grid-cols-3 gap-3 mb-3">
          {secondaryImages.map((img, index) => (
            <div key={index} className="relative group">
              <img
                src={img}
                alt={`Secondary ${index + 1}`}
                className="w-full h-24 object-cover rounded-lg border border-gray-200"
              />
              <button
                type="button"
                onClick={() => removeSecondaryImage(index)}
                className="absolute top-1 right-1 bg-red-500 text-white p-1 rounded-full opacity-0 group-hover:opacity-100 transition"
              >
                <MdClose size={12} />
              </button>
            </div>
          ))}

          {secondaryImages.length < 3 && inputMode === 'upload' && (
            <label
              htmlFor="secondary-upload"
              className="flex flex-col items-center justify-center h-24 border-2 border-dashed border-gray-300 rounded-lg cursor-pointer hover:border-gray-400 transition-colors"
            >
              <MdImage className="w-6 h-6 text-gray-400 mb-1" />
              <span className="text-[10px] text-gray-500 font-semibold">Add</span>
              <input
                id="secondary-upload"
                type="file"
                accept="image/jpeg,image/jpg,image/png,image/webp"
                onChange={handleSecondaryFileChange}
                disabled={uploadingSecondary}
                className="hidden"
              />
            </label>
          )}
        </div>

        {inputMode === 'url' && secondaryImages.length < 3 && (
          <div className="flex gap-2">
            <input
              id="secondary-url-input"
              type="url"
              placeholder="https://example.com/image.jpg"
              className="flex-1 text-xs border border-gray-200 rounded-xl px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-300 text-gray-700"
            />
            <button
              type="button"
              onClick={() => handleUrlSubmit(false)}
              className="control px-4 py-2 bg-gray-950 text-white rounded-xl hover:bg-gray-800 transition text-xs font-semibold"
            >
              Add
            </button>
          </div>
        )}
      </div>
    </div>
  );
}