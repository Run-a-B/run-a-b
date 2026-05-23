import { useState, useRef, useEffect } from "react";

interface DropdownProps {
  value: string;
  onChange: (v: string) => void;
  options: string[];
  placeholder?: string;
}

export default function Dropdown({ value, onChange, options, placeholder }: DropdownProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handler(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  const label = value || placeholder || options[0];

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen(v => !v)}
        className={`flex items-center gap-2 border rounded-xl px-4 py-2 text-sm font-medium transition-colors ${
          open ? "border-primary-500 text-primary-600" : "border-gray-200 text-gray-700 hover:border-gray-300"
        }`}
      >
        {label}
        <svg
          width="14" height="14" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"
          className={`transition-transform ${open ? "rotate-180" : ""}`}
        >
          <polyline points="6 9 12 15 18 9"/>
        </svg>
      </button>

      {open && (
        <ul className="absolute top-full mt-1.5 left-0 bg-white border border-gray-200 rounded-xl shadow-lg py-1.5 min-w-36 z-50">
          {options.map(option => {
            const isSelected = value === option;
            return (
              <li key={option}>
                <button
                  onClick={() => { onChange(option); setOpen(false); }}
                  className={`w-full flex items-center gap-2 px-4 py-2 text-sm text-left transition-colors hover:bg-gray-50 ${
                    isSelected ? "text-gray-900 font-medium" : "text-gray-600"
                  }`}
                >
                  <span className="w-3 text-gray-900 text-xs">
                    {isSelected ? "✓" : ""}
                  </span>
                  {option}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
